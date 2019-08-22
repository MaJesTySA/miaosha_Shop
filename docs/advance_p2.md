[【性能优化，打造亿级秒杀系统笔记】上](https://github.com/MaJesTySA/miaosha_Shop/blob/master/docs/advance.md)

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

之前下单，是**直接操作数据库**，那么可不可以**操作缓存**？在缓存中下单？答案是可以的。如果要在缓存中扣减库存，需要解决**两个**问题，第一个是活动开始前，将数据库的库存信息，同步到缓存中。第二个是下单之后，要将缓存中的库存信息同步到数据库中。这就需要用到**异步消息队列**——也就是**RocketMQ**。

#### RocketMQ

RocketMQ是阿里巴巴在RabbitMQ基础上改进的一个消息中间件，具体的就不赘述了。

只是要特别说明一下，默认的RocketMQ**配置很坑**（`Xms4g Xmx4g Xmn2g`），会导致Jav**a内存不足**的问题。需要修改`mqnamesrv.xml`，将`NewSize`、`MaxNewSize`、`PermSize`、`MaxPermSize`设置为自己服务器可承受值。

此外，`mqnamesrv`甚至不能用`localhost`启动，必须是本机公网IP，否则报`RemotingTooMuchRequestException`。

#### 同步数据库库存到缓存

`PromoService`新建一个`publishPromo`的方法，把数据库的缓存存到Redis里面去。

```java
public void publishPromo(Integer promoId) {
    //通过活动id获取活动
    PromoDO promoDO=promoDOMapper.selectByPrimaryKey(promoId);
    if(promoDO.getItemId()==null||promoDO.getItemId().intValue()==0)
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

在`mq.MyProducer`类里面新注入一个`TransactionMQProducer`类，与`DefaultMQProducer`类似，也需要设置服务器地址、命名空间等。

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

不要以为这样就万事大吉了，上述流程还有一个漏洞，就是当执行`orderService.createOrder`后，突然**又宕机了**，根本没有返回，这个时候事务型消息就会进入`UNKOWN`状态，我们需要处理这个状态。

在匿名类`TransactionListener`里面，还需要覆写`checkLocalTransaction`方法，这个方法就是用来处理`UNKOWN`状态的。应该怎么处理？这就需要引入**库存流水**。

