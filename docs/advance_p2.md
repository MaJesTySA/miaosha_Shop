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

这还没完，最后还要对`stock`**库存表进行-1`update`操作**，对`order_info`**订单信息表进行添加`insert`操作**，对`item`**商品信息表进行销量+1`update`操作**。仅仅一个下单，就有**6次**数据库I/O操作，此外，减库存操作还存在行锁阻塞，所以下单接口并发性能很低。

## 交易验证优化

查询用户信息，是为了**用户风控策略**。判断用户信息是否存在是最进本的策略，在企业级中，还可以判断用户是否异常？异地登录等等。用户风控的信息，实际上可以缓存化，放到Redis里面。

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

之前扣减库存的操作，会执行`update stock set stock = stock -#{amount} where item_id = #{itemId} and stock >= #{amount}`这条SQL语句。如果`where`条件的**`item_id`字段没有索引**，**那么会锁表**，性能很低。所以先查看`item_id`字段是否有索引，没有的话，使用`alter table stock add UNIQUE INDEX item_id_index(item_id)`，为`item_id`字段添加一个**唯一索引**，这样在修改的时候，只会**锁行**。

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

编写`asycnReduceStock`方法，实现异步扣减库存。

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
2. 消费端从数据库扣减操作执行失败，如何处理？（这里默认会成功）
3. 下单失败无法正确回补库存（比如用户取消订单）。

所以需要引入**事务型消息**。

