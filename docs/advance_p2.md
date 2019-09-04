[【性能优化，打造亿级秒杀系统笔记】上](https://github.com/MaJesTySA/miaosha_Shop/blob/master/docs/advance_p1.md)

------

- [交易优化之缓存库存](#交易优化之缓存库存)
  - [交易接口瓶颈](#交易接口瓶颈)
  - [交易验证优化](#交易验证优化)
    - [用户校验缓存优化](#用户校验缓存优化)
    - [活动校验缓存优化](#活动校验缓存优化)
    - [缓存优化后的效果](#缓存优化后的效果)
  - [库存扣减优化](#库存扣减优化)
    - [索引优化](#索引优化)
    - [库存扣减缓存优化](#库存扣减缓存优化)
      - [RocketMQ](#rocketmq)
      - [同步数据库库存到缓存](#同步数据库库存到缓存)
      - [同步缓存库存到数据库（异步扣减库存）](#同步缓存库存到数据库（异步扣减库存）)
      - [异步扣减库存存在的问题](#异步扣减库存存在的问题)
  - [小结](#小结)
  - [接下来的优化方向](#接下来的优化方向)
- [交易优化之事务型消息](#交易优化之事务型消息)
  - [异步消息发送时机问题](#异步消息发送时机问题)
    - [解决方法](#解决方法)
  - [事务提交问题](#事务提交问题)
    - [解决方法](#解决方法-1)
  - [事务型消息](#事务型消息)
    - [更新下单流程](#更新下单流程)
  - [小结](#小结-1)
  - [接下来的优化方向](#接下来的优化方向-1)
- [库存流水](#库存流水)
  - [下单操作的处理](#下单操作的处理)
  - [UNKNOWN状态处理](#unknown状态处理)
  - [库存售罄处理](#库存售罄处理)
  - [小结](#小结-2)
    - [可以改进的地方](#可以改进的地方)
  - [接下来的优化方向](#接下来的优化方向-2)
- [流量削峰](#流量削峰)
  - [业务解耦—秒杀令牌](#业务解耦秒杀令牌)
  - [限流—令牌大闸](#限流令牌大闸)
    - [令牌大闸限流缺点](#令牌大闸限流缺点)
  - [限流—队列泄洪](#限流队列泄洪)
  - [小结](#小结-3)
  - [接下来的优化方向](#接下来的优化方向-3)
- [防刷限流](#防刷限流)
  - [验证码技术](#验证码技术)
  - [限流方案—限并发](#限流方案限并发)
  - [限流方案—令牌桶/漏桶](#限流方案令牌桶漏桶)
    - [令牌桶](#令牌桶)
    - [漏桶](#漏桶)
    - [区别](#区别)
  - [限流力度](#限流力度)
  - [限流范围](#限流范围)
  - [RateLimiter限流实现](#ratelimiter限流实现)
  - [防刷技术](#防刷技术)
    - [传统防刷技术](#传统防刷技术)
    - [黄牛为什么难防](#黄牛为什么难防)
    - [防黄牛方案](#防黄牛方案)
  - [小结](#小结-4)

------

# 交易优化之缓存库存

## 交易接口瓶颈

发送20*200个请求压测`createOrder`接口，TPS只有**280**左右，平均响应时间**460**毫秒。应用服务器`us`占用高达**75%**，1分钟的`load average`高达**2.21**，可见压力很大。相反，数据库服务器的压力则要小很多。

原因在于，在`OrderService.createOrder`方法里面，首先要去数据库**查询商品信息**，而在查询商品信息的过程中，又要去**查询秒杀活动信息**，最后还要查询**用户信息**。

```java
//查询商品信息的过程中，也会查询秒杀活动信息。
ItemModel itemModel=itemService.getItemById(itemId);
if(itemModel==null)
    throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
//查询用户信息
UserModel userModel=userService.getUserById(userId);
```

这还没完，最后还要对`stock`**库存表进行-1`update`操作**，对`order_info`**订单信息表进行添加`insert`操作**，对`item`**商品信息表进行销量+1`update`操作**。仅仅一个下单，就有**6次**数据库I/O操作，此外，减库存操作还存在**行锁阻塞**，所以下单接口并发性能很低。

## 交易验证优化

查询用户信息，是为了**用户风控策略**。判断用户信息是否存在是最基本的策略，在企业级中，还可以判断用户状态是否异常，是否异地登录等等。用户风控的信息，实际上可以缓存化，放到Redis里面。

查询商品信息、活动信息，是为了**活动校验策略**。商品信息、活动信息，也可以存入缓存中。活动信息，由于具有**时效性**，需要具备紧急下线的能力，可以编写一个接口，清除活动信息的缓存。

### 用户校验缓存优化

思路很简单，就是先从Redis里面获取用户信息，没有再去数据库里查，并存到Redis里面。`UserService`新开一个`getUserByIdInCache`的方法。

```java
public UserModel getUserByIdInCache(Integer id) {
    UserModel userModel= (UserModel) redisTemplate.opsForValue().get("user_validate_"+id);
    if(userModel==null){
        userModel=this.getUserById(id);
        redisTemplate.opsForValue().set("user_validate_"+id,userModel);
        redisTemplate.expire("user_validate_"+id,10, TimeUnit.MINUTES);
    }
    return userModel;
}
```

### 活动校验缓存优化

跟用户校验类似，`ItemService`新开一个`getItemByIdInCache`方法。

```java
public ItemModel getItemByIdInCache(Integer id) {
    ItemModel itemModel=(ItemModel)redisTemplate.opsForValue().get("item_validate_"+id);
    if(itemModel==null){
        itemModel=this.getItemById(id);
        redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
         redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
    }
    return itemModel;
}
```

### 缓存优化后的效果

之前压测1000*20个请求，TPS在**450**左右，平均响应**1500毫秒**。

优化之后，TPS在**1200**左右，平均响应**600毫秒**，可见效果十分好。

## 库存扣减优化

### 索引优化

之前扣减库存的操作，会执行`update stock set stock = stock -#{amount} where item_id = #{itemId} and stock >= #{amount}`这条SQL语句。如果`where`条件的`item_id`字段没有**索引**，那么会**锁表**，性能很低。所以先查看`item_id`字段是否有索引，没有的话，使用`alter table stock add UNIQUE INDEX item_id_index(item_id)`，为`item_id`字段添加一个**唯一索引**，这样在修改的时候，只会**锁行**。

### 库存扣减缓存优化

之前下单，是**直接操作数据库**，一旦秒杀活动开始，大量的流量涌入扣减库存接口，**数据库压力很大**。那么可不可以先在**缓存中**下单？答案是可以的。如果要在缓存中扣减库存，需要解决**两个**问题，第一个是活动开始前，将数据库的库存信息，同步到缓存中。第二个是下单之后，要将缓存中的库存信息同步到数据库中。这就需要用到**异步消息队列**——也就是**RocketMQ**。

#### RocketMQ

RocketMQ是阿里巴巴在RabbitMQ基础上改进的一个消息中间件，具体的就不赘述了。

只是要特别说明一下，默认的RocketMQ**配置很坑**（`Xms4g Xmx4g Xmn2g`），会导致Java**内存不足**的问题。需要修改`mqnamesrv.xml`，将`NewSize`、`MaxNewSize`、`PermSize`、`MaxPermSize`设置为自己服务器可承受值。

此外，`mqnamesrv`甚至不能用`localhost`启动，必须是本机公网IP，否则报`RemotingTooMuchRequestException`。

#### 同步数据库库存到缓存

`PromoService`新建一个`publishPromo`的方法，把数据库的缓存存到Redis里面去。

```java
public void publishPromo(Integer promoId) {
    //通过活动id获取活动
    PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
    if(promoDO.getItemId()==null || promoDO.getItemId().intValue()==0)
        return;
    ItemModel itemModel=itemService.getItemById(promoDO.getItemId());
    //库存同步到Redis
    redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());
}
```

这里需要注意的是，当我们把**库存存到Redis的时候**，**商品可能被下单**，这样数据库的库存和Redis的库存就**不一致**了。解决方法就是活动**未开始**的时候，商品是**下架状态**，不能被下单。

最后，在`ItemService`里面修改`decreaseStock`方法，在Redis里面扣减库存。

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    // 老方法，直接在数据库减
    // int affectedRow=itemStockDOMapper.decreaseStock(itemId,amount);
    long affectedRow=redisTemplate.opsForValue().
                increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    return (affectedRow >= 0);
}
```

#### 同步缓存库存到数据库（异步扣减库存）

引入RocketMQ相应`jar`包，在Spring Boot配置文件中添加MQ配置。

```properties
mq.nameserver.addr=IP:9876
mq.topicname=stock
```

新建一个`mq.MQProducer`类，编写`init`方法，初始化生产者。

```java
public class MqProducer {
    private DefaultMQProducer producer;
    //即是IP:9867
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    //即是stock
    @Value("${mq.topicname}")
    private String topicName;
   
    @PostConstruct
    public void init() throws MQClientException {
        //Producer初始化，Group对于生产者没有意义，但是消费者有意义
        producer=new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();
    }
}
```

编写`asyncReduceStock`方法，实现异步扣减库存。

```java
public boolean asyncReduceStock(Integer itemId, Integer amount)  {
    Map<String,Object> bodyMap=new HashMap<>();
    bodyMap.put("itemId",itemId);
    bodyMap.put("amount",amount);
    //创建消息
    Message message=new Message(topicName,"increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
    //发送消息
    try {
        producer.send(message);
    } catch (MQClientException e) {
      ···
        return false;
    }
    return true;
}
```

新建一个`mq.MqConsumer`类，与`MqProducer`类类似，也有一个`init`方法，实现**异步扣减库存**的逻辑。

```java
public class MqConsumer {
    private DefaultMQPushConsumer consumer;
    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    @PostConstruct
    public void init() throws MQClientException {
        consumer=new DefaultMQPushConsumer("stock_consumer_group");
        //监听名为topicName的话题
        consumer.setNamesrvAddr(nameAddr);
        //监听topicName话题下的所有消息
        consumer.subscribe(topicName,"*");
        //这个匿名类会监听消息队列中的消息
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
                //实现缓存数据真正到数据库扣减的逻辑
                //从消息队列中获取消息
                Message message=list.get(0);
                //反序列化消息
                String jsonString=new String(message.getBody());
                Map<String,Object> map=JSON.parseObject(jsonString, Map.class);
                Integer itemId= (Integer) map.get("itemId");
                Integer amount= (Integer) map.get("amount");
                //去数据库扣减库存
                itemStockDOMapper.decreaseStock(itemId,amount);
                //返回消息消费成功
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
    }
}
```

`ItemService.decreaseStock`方法也要做更改：

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    long affectedRow=redisTemplate.opsForValue().
                increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    //>0，表示Redis扣减成功
    if(affectedRow>=0){
        //发送消息到消息队列，准备异步扣减
        boolean mqResult = mqProducer.asyncReduceStock(itemId,amount);
        if (!mqResult){
            //消息发送失败，需要回滚Redis
            redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
            return false;
        }
        return true;
    } else {
        //Redis扣减失败，回滚
        redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
        return false;
    }
}
```

#### 异步扣减库存存在的问题

1. 如果发送消息失败，只能回滚Redis。
2. 消费端从数据库扣减操作执行失败，如何处理（这里默认会成功）？
3. 下单失败无法正确回补库存（比如用户取消订单）。

所以需要引入**事务型消息**。

## 小结

这一章我们

1. 首先对**交易验证**进行了优化，把对用户、商品、活动的查询从数据库转移到了缓存中，优化效果明显。
2. 随后，我们优化了减库存的逻辑，一是添加了索引，从锁表变成了锁行；二是将减库存的操作也移到了缓存中，先从缓存中扣，再从数据库扣。这就涉及到了**异步减库存**，所以需要引入**消息中间件**。

## 接下来的优化方向

正如**异步扣减库存存在的问题**所述，这么处理还有许多漏洞，下一章将会详解。

# 交易优化之事务型消息

## 异步消息发送时机问题

目前扣减库存的事务`ItemService.decreaseStock`是封装在`OrderService.createOrder`事务里面的。在扣减Redis库存、发送异步消息之后，还有订单入库、增加销量的操作。如果这些操作失败，那么`createOrder`**事务会回滚**，`decreaseStock`**事务也回滚**，但是Redis的**扣减操作却不能回滚**，会导致数据不一致。

### 解决方法

解决的方法就是在订单入库、增加销量成功之后，再发送异步消息，`ItemService.decreaseStock`只**负责扣减Redis库存**，**不发送异步消息**。

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    long affectedRow=redisTemplate.opsForValue().
                increment("promo_item_stock_"+itemId,amount.intValue()*-1);
    //>0，表示Redis扣减成功
    if(affectedRow>=0){
        //抽离了发送异步消息的逻辑
        return true;
    } else {
        //Redis扣减失败，回滚
        increaseStock(itemId, amount)
        return false;
    }
}

public boolean increaseStock(Integer itemId, Integer amount) {
    redisTemplate.opsForValue().increment("promo_item_stock_"+itemId,amount.intValue());
    return true;
}
```

将发送异步消息的逻辑抽取出来：

```java
//ItemService
public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
    return mqProducer.asyncReduceStock(itemId, amount);
}
```

再在`OrderService.createOrder`里面调用：

```java
···
//订单入库
orderDOMapper.insertSelective(orderDO);
//销量增加
itemService.increaseSales(itemId,amount);
//执行完最后一步才发送异步消息
boolean mqResult=itemService.asyncDecreaseStock(itemId,amount);
    if(!mqResult){
        //回滚redis库存
        itemService.increaseStock(itemId,amount);
        throw new BizException(EmBizError.MQ_SEND_FAIL);
    }
```

这样，就算订单入库失败、销量增加失败、消息发送失败，都能保证缓存和数据库的一致性。

## 事务提交问题

但是这么做，依然有问题。Spring的`@Transactional`标签，会在**事务方法返回后才提交**，如果提交的过程中，发生了异常，则数据库回滚，但是Redis库存已扣，还是无法保证一致性。我们需要在**事务提交成功后**，**再发送异步消息**。

### 解决方法

Spring给我们提供了`TransactionSynchronizationManager.registerSynchronization`方法，这个方法的传入一个`TransactionSynchronizationAdapter`的匿名类，通过`afterCommit`方法，在**事务提交成功后**，执行**发送消息操作**。

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
    @Override
    public void afterCommit() {
    boolean mqResult=itemService.asyncDecreaseStock(itemId,amount);
    if(!mqResult){
        itemService.increaseStock(itemId,amount);
        throw new BizException(EmBizError.MQ_SEND_FAIL);
    }
}
```

## 事务型消息

上面的做法，依然不能保证万无一失。假设现在**事务提交成功了**，等着执行`afterCommit`方法，这个时候**突然宕机了**，那么**订单已然入库**，**销量已然增加**，但是**去数据库扣减库存的这条消息**却“**丢失**”了。这里就需要引入RocketMQ的事务型消息。

所谓事务型消息，也会被发送到消息队列里面，这条消息处于`prepared`状态，`broker`会接受到这条消息，**但是不会把这条消息给消费者消费**。

处于`prepared`状态的消息，会执行`TransactionListener`的`executeLocalTransaction`方法，根据执行结果，**改变事务型消息的状态**，**让消费端消费或是不消费**。

在`mq.MqProducer`类里面新注入一个`TransactionMQProducer`类，与`DefaultMQProducer`类似，也需要设置服务器地址、命名空间等。

新建一个`transactionAsyncReduceStock`的方法，该方法使用**事务型消息**进行异步扣减库存。

```java
// 事务型消息同步库存扣减消息
public boolean transactionAsyncReduceStock(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) {
    Map<String, Object> bodyMap = new HashMap<>();
    bodyMap.put("itemId", itemId);
    bodyMap.put("amount", amount);
    //用于执行orderService.createOrder的传参
    Map<String, Object> argsMap = new HashMap<>();
    argsMap.put("itemId", itemId);
    argsMap.put("amount", amount);
    argsMap.put("userId", userId);
    argsMap.put("promoId", promoId);

    Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
    try {
        //注意，发送的是sendMessageInTransaction
        transactionMQProducer.sendMessageInTransaction(message, argsMap);
    } catch (MQClientException e) {
        e.printStackTrace();
        return false;
    }
    return true;
}
```

这样，就会发送一个事务型消息到`broke`，而处于`prepared`状态的事务型消息，会执行`TransactionListener`的`executeLocalTransaction`方法：

```java
transactionMQProducer.setTransactionListener(new TransactionListener() {
    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object args) {
    //在事务型消息中去进行下单
    Integer itemId = (Integer) ((Map) args).get("itemId");
    Integer promoId = (Integer) ((Map) args).get("promoId");
    Integer userId = (Integer) ((Map) args).get("userId");
    Integer amount = (Integer) ((Map) args).get("amount");
    try {
        //调用下单接口
        orderService.createOrder(userId, itemId, promoId, amount);
    } catch (BizException e) {
        e.printStackTrace();
        //发生异常就回滚消息
        return LocalTransactionState.ROLLBACK_MESSAGE;
    }
    return LocalTransactionState.COMMIT_MESSAGE;
}
```

这样，在**事务型消息中去执行下单操作**，下单失败，则消息回滚，**不会去数据库扣减库存**。下单成功，则消息被消费，**扣减数据库库存**。

### 更新下单流程

之前的下单流程是：在`OrderController`里面调用了`OrderService.createOrder`方法，然后在该方法最后发送了异步消息，会导致异步消息丢失的问题。所以我们引入了**事务型消息**。

现在的下单流程是：在`OrderController`里面直接调用`MqProducer.transactionAsyncReduceStock`方法，发送一个事务型消息，然后在**事务型消息中调用`OrderService.createOrder`方法**，进行下单。

## 小结

这一章我们

1. 首先解决了**发送异步消息时机**的问题，之前是在`ItemService.decreaseStock`，当在Redis里面扣减成功后，发送异步消息。这样会导致数据库回滚，但Redis无法回滚的问题。所以我们把发送异步消息提到所有下单操作完成之后。
2. 其次，由于Spring的`@Transactional`标签是在方法返回后，才提交事务，如果返回阶段出了问题，那么数据库回滚了，但是缓存的库存却扣了。所以，我们使用了**`afterCommit`**方法。
3. 最后，如果在执行`afterCommit`的时候，发生了异常，那么消息就发不出去，又会导致数据一致性问题。所以我们通过使用**事务型消息**，把**下单操作包装在异步扣减消息里面**，让下单操作跟扣减消息**同生共死**。

## 接下来的优化方向

不要以为这样就万事大吉了，上述流程还有一个漏洞，就是当执行`orderService.createOrder`后，突然**又宕机了**，根本没有返回，这个时候事务型消息就会进入`UNKNOWN`状态，我们需要处理这个状态。

在匿名类`TransactionListener`里面，还需要覆写`checkLocalTransaction`方法，这个方法就是用来处理`UNKNOWN`状态的。应该怎么处理？这就需要引入**库存流水**。

# 库存流水

数据库新建一张`stock_log`的表，用来记录库存流水，添加一个`ItemService.initStockLog`方法。

```java
public String initStockLog(Integer itemId, Integer amount) {
    StockLogDO stockLogDO = new StockLogDO();
    stockLogDO.setItemId(itemId);
    stockLogDO.setAmount(amount);
    stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-", ""));
    //1表示初始状态，2表示下单扣减库存成功，3表示下单回滚
    stockLogDO.setStatus(1);
    stockLogDOMapper.insertSelective(stockLogDO);
    return stockLogDO.getStockLogId();
}
```

用户请求后端`OrderController.createOrder`接口，我们先初始化库存流水的状态，再调用事务型消息去下单。

```java
//OrderController
//先检验用户登录信息
String token = httpServletRequest.getParameterMap().get("token")[0];
if (StringUtils.isEmpty(token)) {
    throw new BizException(EmBizError.USER_NOT_LOGIN, "用户还未登录，不能下单");
}
UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
if (userModel == null) {
    throw new BizException(EmBizError.USER_NOT_LOGIN, "登录过期，请重新登录");
}

//初始化库存流水
String stockLogId = itemService.initStockLog(itemId, amount);

//发送事务型消息，完成下单逻辑
if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
    throw new BizException(EmBizError.UNKNOWN_ERROR, "下单失败");
}
```

事务型消息会调用`OrderService.createOrder`方法，执行Redis扣减库存、订单入库、销量增加的操作，当这些操作都完成后，就说明下单完成了，**等着异步更新数据库了**。那么需要修改订单流水的状态。

```java
//OrderService.createOrder
//订单入库
orderDOMapper.insertSelective(orderDO);
//增加销量
itemService.increaseSales(itemId, amount);
StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
if (stockLogDO == null)
    throw new BizException(EmBizError.UNKNOWN_ERROR);
//设置库存流水状态为成功
stockLogDO.setStatus(2);
stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
```

## 下单操作的处理

异步更新数据库，需要事务型消息从`prepare`状态变成`commit`状态。假如此时`orderService.createOrder`**本身发生了异常**，那么就回滚事务型消息，并且返回`LocalTransactionState.ROLLBACK_MESSAGE`，这个下单操作就会被取消。

如果**本身没有发生异常**，那么就返回`LocalTransactionState.COMMIT_MESSAGE`，此时事务型消息会从`prepare`状态变为`commit`状态，接着被消费端消费，异步扣减库存。

```java
//MqProducer.TransactionListener().executeLocalTransaction()
try {
    orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
} catch (BizException e) {
    e.printStackTrace();
    //如果发生异常，createOrder已经回滚，此时要回滚事务型消息。
    //设置stockLog为回滚状态
StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
    stockLogDO.setStatus(3);
    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
    return LocalTransactionState.ROLLBACK_MESSAGE;
}
return LocalTransactionState.COMMIT_MESSAGE;
```

## UNKNOWN状态处理

如上节结尾所述，如果在执行`createOrder`的时候，突然宕机了，此时事务型消息的状态是`UNKNOWN`，需要在`TransactionListener.checkLocalTransaction`方法中进行处理。

```java
public LocalTransactionState checkLocalTransaction(MessageExt message) {
    //根据是否扣减库存成功，来判断要返回COMMIT，ROLLBACK还是UNKNOWN
    String jsonString = new String(message.getBody());
    Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
    String stockLogId = (String) map.get("stockLogId");
    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
    if (stockLogDO == null)
        return LocalTransactionState.UNKNOW;
    //订单操作已经完成，等着异步扣减库存，那么就提交事务型消息
    if (stockLogDO.getStatus() == 2) {
        return LocalTransactionState.COMMIT_MESSAGE;
    //订单操作还未完成，需要执行下单操作，那么就维持为prepare状态
    } else if (stockLogDO.getStatus() == 1) {
        return LocalTransactionState.UNKNOW;
    }
    //否则就回滚
    return LocalTransactionState.ROLLBACK_MESSAGE;
}
```

## 库存售罄处理

现在是用户请求一次`OrderController.createOrder`就初始化一次流水，但是如果10000个用户抢10个商品，就会初始化10000次库存流水，这显然是不行的。

解决的方法是在`ItemService.decreaseStock`中，如果库存没有了，就打上“**售罄标志**”。

```java
public boolean decreaseStock(Integer itemId, Integer amount) {
    long affectedRow = redisTemplate.opsForValue().
                increment("promo_item_stock_" + itemId, amount.intValue() * -1);
    if (affectedRow > 0) {
        return true;
    } else if (affectedRow == 0) {
        //打上售罄标识
        redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
        return true;
    } else {
        increaseStock(itemId, amount);
        return false;
    }
}
```

在`OrderController.createOrder`初始化流水之前，先判断一下是否售罄，售罄了就直接抛出异常。

```java
//是否售罄
if (redisTemplate.hasKey("promo_item_stock_invalid_"+itemId))
    throw new BizException(EmBizError.STOCK_NOT_ENOUGH);
String stockLogId = itemService.initStockLog(itemId, amount);
```

## 小结

这一节通过引入库存流水，来记录库存的状态，以便在**事务型消息处于不同状态时进行处理**。

事务型消息提交后，会在`broker`里面处于`prepare`状态，也即是`UNKNOWN`状态，等待被消费端消费，或者是回滚。`prepare`状态下，会执行`OrderService.createOrder`方法。

此时有两种情况：

1. `createOrder`执行完**没有宕机**，要么**执行成功**，要么**抛出异常**。**执行成功**，那么就说明下单成功了，订单入库了，Redis里的库存扣了，销量增加了，**等待着异步扣减库存**，所以将事务型消息的状态，从`UNKNOWN`变为`COMMIT`，这样消费端就会消费这条消息，异步扣减库存。抛出异常，那么订单入库、Redis库存、销量增加，就会被数据库回滚，此时去异步扣减的消息，就应该“丢弃”，所以发回`ROLLBACK`，进行回滚。
2. `createOrder`执行完**宕机**了，那么这条消息会是`UNKNOWN`状态，这个时候就需要在`checkLocalTransaction`进行处理。如果`createOrder`执行完毕，此时`stockLog.status==2`，就说明下单成功，需要去异步扣减库存，所以返回`COMMIT`。如果`status==1`，说明下单还未完成，还需要继续执行下单操作，所以返回`UNKNOWN`。如果`status==3`，说明下单失败，需要回滚，不需要异步扣减库存，所以返回`ROLLBACK`。

### 可以改进的地方

目前只是扣减库存异步化，实际上销量逻辑和交易逻辑都可以异步化，这里就不赘述了。

## 接下来的优化方向

目前下单接口会被脚本不停地刷，影响正常用户的体验。此外，验证逻辑和下单逻辑强关联，耦合度比较高。最后，验证逻辑也比较复杂。接下来会引入流量削峰技术。

# 流量削峰

秒杀秒杀，就是在活动开始的一瞬间，有大量流量涌入，优化不当，会导致服务器停滞，甚至宕机。所以引入流量削峰技术十分有必要。

## 业务解耦—秒杀令牌

之前的**验证逻辑**和**下单逻辑**都耦合在`OrderService.createOrder`里面，现在利用秒杀令牌，使校验逻辑和下单逻辑分离。

`PromoService`新开一个`generateSecondKillToken`，将活动、商品、用户信息校验逻辑封装在里面。

```java
public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
    //判断库存是否售罄，若Key存在，则直接返回下单失败
    if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId))
        return null;
    PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
    PromoModel promoModel=convertFromDataObj(promoDO);
    if(promoModel==null) return null;
    if(promoModel.getStartDate().isAfterNow()) {
        promoModel.setStatus(1);
    }else if(promoModel.getEndDate().isBeforeNow()){
        promoModel.setStatus(3);
    }else{
        promoModel.setStatus(2);
    }
    //判断活动是否正在进行
    if(promoModel.getStatus()!=2) return null;
    //判断item信息是否存在
    ItemModel itemModel=itemService.getItemByIdInCache(itemId);
    if(itemModel==null) return null;
    //判断用户是否存在
    UserModel userModel=userService.getUserByIdInCache(userId);
    if(userModel==null) return null;
    //生成Token，并且存入redis内，5分钟时限
    String token= UUID.randomUUID().toString().replace("-","");
    redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,token);
    redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId, 5,TimeUnit.MINUTES);
        return token;
}
```

这样，`OrderService.createOrder`的校验逻辑就可以删掉了。

`OrderController`新开一个`generateToken`接口，以便前端请求，返回令牌。

```java
@RequestMapping(value = "/generatetoken",···)
@ResponseBody
public CommonReturnType generateToken(···) throws BizException {
    //用户登录状态校验
    ···
    //获取秒杀访问令牌
    String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
if (promoToken == null)
    throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
return CommonReturnType.create(promoToken);
}

```

前端在点击“**下单**”后，首先会请求`generateToken`接口，返回秒杀令牌。然后将秒杀令牌`promoToken`作为参数，再去请求后端`createOrder`接口：

```java
@RequestMapping(value = "/createorder",···)
@ResponseBody
public CommonReturnType createOrder(··· @RequestParam(name = "promoToken", required = false) String promoToken) throws BizException {
    ···
    //校验秒杀令牌是否正确
    if (promoId != null) {
        String inRedisPromoToken = (String) redisTemplate.opsForValue().
                    get("promo_token_" + promoId + "_userid_" + userModel.getId() + "_itemid_" + itemId);
    if (inRedisPromoToken == null) 
        throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "令牌校验失败");
    if (!StringUtils.equals(promoToken, inRedisPromoToken)) 
        throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "令牌校验失败");
}

```

这样就彻底完成了校验逻辑和下单逻辑的分离。现在的问题是，假设有1E个用户请求下单，那么就会生成1E的令牌，这是十分消耗性能的，所以接下来会引入**秒杀大闸进行限流**。

## 限流—令牌大闸

大闸的意思就是**令牌的数量是有限的**，当令牌用完时，就不再发放令牌了，那么下单将无法进行。之前我们通过`PromoService.publishPromo`将库存发布到了Redis上，现在我们将令牌总量也发布到Redis上，这里我们设定令牌总量是库存的5倍。

```java
public void publishPromo(Integer promoId) {
    ···
    //库存同步到Redis
    redisTemplate.opsForValue().set("promo_item_stock_" + itemModel.getId(), itemModel.getStock());
    //大闸限制数量设置到redis内
    redisTemplate.opsForValue().set("promo_door_count_" + promoId, itemModel.getStock().intValue() * 5);
}

```

接下来，在`PromoService.generateSecondKillToken`方法中，在生成令牌之前，首先将Redis里的令牌总量减1，然后再判断是否剩余，如果<0，直接返回null。

```java
//获取大闸数量
long result = redisTemplate.opsForValue().
                increment("promo_door_count_" + promoId, -1);
if (result < 0) 
    return null;
//令牌生成       

```

这样，当令牌总量为0时，就不再发放令牌，也就无法下单了。

### 令牌大闸限流缺点

当商品种类少、库存少的时候，令牌大闸效果还不错。但是一旦参与活动的商品库存太大，比如10000个，那么一秒钟也有上十万的流量涌入，限制能力是很弱的。所以需要**队列泄洪**。

## 限流—队列泄洪

队列泄洪，就是让多余的请求**排队等待**。**排队**有时候比**多线程**并发效率更高，多线程毕竟有锁的竞争、上下文的切换，很消耗性能。而排队是无锁的，单线程的，某些情况下效率更高。

比如Redis就是**单线程模型**，多个用户同时执行`set`操作，只能一一等待。

比如MySQL的`insert`和`update`语句，会维护一个行锁。阿里SQL就不会，而是让多个SQL语句排队，然后依次执行。

像支付宝就使用了队列泄洪，双11的时候，支付宝作为网络科技公司，可以承受很高的TPS，但是下游的各个银行，无法承受这么高的TPS。支付宝维护了一个“拥塞窗口”，慢慢地向下游银行发送流量，保护下游。

那对于我们的项目，什么时候引入“队列泄洪”呢？在`OrderController`里面，之前拿到秒杀令牌后，就要开始执行下单的业务了。现在，我们把**下单业务**封装到一个**固定大小的线程池中**，一次**只处理固定大小的请求**。

在`OrderController`里面引入`j.u.c.ExcutorService`，创建一个`init`方法，初始化线程池。

```java
@PostConstruct
public void init() {
    //20个线程的线程池
    executorService = Executors.newFixedThreadPool(20);
}

```

在拿到秒杀令牌后，使用线程池来处理下单请求。

```java
Future<Object> future = executorService.submit(new Callable<Object>() {
    @Override
    public Object call() throws Exception {
        String stockLogId = itemService.initStockLog(itemId, amount);
        if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
            throw new BizException(EmBizError.UNKNOWN_ERROR, "下单失败");
        }
        return null;
    }
});
try {
future.get();
} catch (InterruptedException e) {
    ···
}

```

这样，就算瞬间涌入再多流量，得到处理的也就20个，其它全部等待。

## 小结

这一章我们

1. 使用秒杀令牌，实现了校验业务和下单业务的分离。同时为秒杀大闸做了铺垫。
2. 使用秒杀大闸，实现了限流的第一步，限制了流量的总量。
3. 使用队列泄洪，实现了限流的第二步，同一时间只有部分请求得到处理。

## 接下来的优化方向

接下来将会引入防刷限流技术，比如验证码技术等。

# 防刷限流

## 验证码技术

之前的流程是，用户点击下单后，会直接拿到令牌然后执行下单流程。现在，用户点击下单后，前端会弹出一个“验证码”，用户输入之后，才能请求下单接口。

`OrderController`新开一个`generateVerifyCode`接口。

```java
@RequestMapping(value = "/generateverifycode",···)
@ResponseBody
public void generateVerifyCode(HttpServletResponse response) throws BizException, IOException {
    ···验证
    //验证用户信息
    Map<String, Object> map = CodeUtil.generateCodeAndPic();
    //生成的验证码存到Redis里，并设置过期时间
    redisTemplate.opsForValue().set("verify_code_" + userModel.getId(), map.get("code"));
    redisTemplate.expire("verify_code_" + userModel.getId(), 10, TimeUnit.MINUTES);
    //生成的图片，响应到前端页面
    ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
}

```

之前获取秒杀令牌的`generateToken`接口，需要添加验证码校验逻辑。

```java
public CommonReturnType generateToken(··· @RequestParam(name = "verifyCode") String verifyCode) throws BizException {
    //验证用户登录信息
    ···
    //验证验证码的有效性
    String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
    if (StringUtils.isEmpty(redisVerifyCode))
        throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "请求非法");
    if (!redisVerifyCode.equalsIgnoreCase(verifyCode))
        throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "请求非法");
    //获取秒杀访问令牌
    ···
}

```

这样，就实现了在下单之前，添加一个验证码，限制部分流量的功能。

## 限流方案—限并发

限制并发量意思就是同一时间**只有一定数量的线程去处理请求**，实现也比较简单，维护一个**全局计数器**，当请求进入接口时，计数器-1，并且判断计数器是否>0，大于0则处理请求，小于0则拒绝等待。

但是一般衡量并发性，是用TPS或者QPS，而该方案由于限制了线程数，自然不能用TPS或者QPS衡量。

## 限流方案—令牌桶/漏桶

### 令牌桶

客户端请求接口，必须先从令牌桶中获取令牌，令牌是由一个“定时器”定期填充的。在一个时间内，令牌的数量是有限的。令牌桶的大小为100，那么TPS就为100。

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/tokenBucket.png)

### 漏桶

客户端请求接口，会向漏桶里面“加水”。漏桶每秒漏出一定数量的“水”，也就是处理请求。只有当漏洞不满时，才能请求。

![](https://raw.githubusercontent.com/MaJesTySA/miaosha_Shop/master/imgs/leekBucket.png)

### 区别

漏桶无法应对**突发流量**，比如突然来10个请求，只能处理一个。但是令牌桶，可以一次性处理10个。所以，令牌桶用得比较多。

## 限流力度

分为**接口维**度和**总维度**，很好理解。接口维度就是限制某个接口的流量，而总维度是限制所有接口的流量。

## 限流范围

分为**集群限流**和**单机限流**，集群限流顾名思义就是限制整个集群的流量，需要用Redis或者其它中间件技术来做统一计数器，往往会产生性能瓶颈。单机限流在负载均衡的前提下效果更好。

## RateLimiter限流实现

`google.guava.RateLimiter`就是令牌桶算法的一个实现类，`OrderController`引入这个类，在`init`方法里面，初始令牌数量为200。

```java
@PostConstruct
    public void init() {
    //20个线程的线程池
    executorService = Executors.newFixedThreadPool(20);
    //200个令牌，即200TPS
    orderCreateRateLimiter = RateLimiter.create(200);
}

```

请求`createOrder`接口之前，会调用`RateLimiter.tryAcquire`方法，看当前令牌是否足够，不够直接抛出异常。

```java
if (!orderCreateRateLimiter.tryAcquire())
     throw new BizException(EmBizError.RATELIMIT);

```

## 防刷技术

排队、限流、令牌只能控制总流量，无法控制黄牛流量。

### 传统防刷技术

1. 限制一个会话（Session、Token）一定时间内请求接口的次数。多会话接入绕开无效，比如黄牛可以开启多个会话。
2. 限制一个IP一定时间内请求接口的次数。容易误伤，某个局域网的正常用户共享一个IP进行访问。而且IP可以被伪造。

### 黄牛为什么难防

1. 模拟硬件设备，比如手机。一个看似正常的用户，可能是用模拟器模拟出来的。
2. 设备牧场，一屋子手机刷接口。
3. 人工作弊，这个最难防，请真人刷接口。

### 防黄牛方案

1. **设备指纹方式**：采集终端设备各项数据，启动应用时生成一个唯一设备指纹。根据对应设备的指纹参数，估计是可疑设备的概率。
2. **凭证系统**：根据设备指纹下发凭证，在关键业务链路上带上凭证并由凭证服务器验证。凭证服务器根据设备指纹参数和风控系统判定凭证的可疑程度。若凭证分数低于设定值，则开启验证。

## 小结

这一节我们

1. 通过引入验证码技术，在发送秒杀令牌之前，再做一层限流。
2. 介绍了三种限流的方案，使用`RateLimiter`实现了令牌桶限流。
3. 介绍了常见的防刷技术以及它们的缺点。介绍了黄牛为什么难防，应该怎样防。

------

# 优化效果总结

## 交易验证优化

| 交易验证优化（1000*20） | TPS  | 平均响应时间/ms | us   | load average |
| ----------------------- | ---- | --------------- | ---- | ------------ |
| 优化前                  | 450  | 1500            | 7.5  | 1分钟2.21    |
| 优化后                  | 1200 | 600             | -    | -            |

