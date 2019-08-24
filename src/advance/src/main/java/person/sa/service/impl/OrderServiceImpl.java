package person.sa.service.impl;

import com.alibaba.druid.sql.ast.expr.SQLCaseExpr;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import person.sa.dao.OrderDOMapper;
import person.sa.dao.SequenceDOMapper;
import person.sa.dao.StockLogDOMapper;
import person.sa.dataobject.OrderDO;
import person.sa.dataobject.SequenceDO;
import person.sa.dataobject.StockLogDO;
import person.sa.error.BizException;
import person.sa.error.EmBizError;
import person.sa.service.ItemService;
import person.sa.service.OrderService;
import person.sa.service.UserService;
import person.sa.service.model.ItemModel;
import person.sa.service.model.OrderModel;
import person.sa.service.model.UserModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrderDOMapper orderDOMapper;
    @Autowired
    private SequenceDOMapper sequenceDOMapper;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BizException {
        //1. 校验下单状态。下单商品是否存在，用户是否合法，购买数量是否正确
        // 无缓存优化:ItemModel itemModel=itemService.getItemById(itemId);

        /*  因为在生成promoToken的时候已经验证过了，下单业务不需要验证，解耦。
            //校验用户和商品信息
            无缓存优化：
            UserModel userModel=userService.getUserById(userId);
            缓存优化：
            ItemModel itemModel=itemService.getItemByIdInCache(itemId);
                if(itemModel==null) throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
            UserModel userModel=userService.getUserByIdInCache(userId);
                if(userModel==null) throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");

           //校验活动信息
            if(promoId!=null){
                //1.校验对应活动是否适用于该商品
                if(promoId.intValue()!=itemModel.getPromoModel().getId()){
                    throw new  BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"活动信息不存在");
                    //2.校验活动是否在进行中
                }else if (itemModel.getPromoModel().getStatus()!=2){
                    throw new  BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"活动还未开始");
                }
            }
        */

        if (amount <= 0 || amount > 99)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "数量信息不存在");

        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if (itemModel == null)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");

        //2. 落单减库存 or 支付减库存。这里是落单减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if (!result)
            throw new BizException(EmBizError.STOCK_NOT_ENOUGH);

        //3. 订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if (promoId != null) {
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        } else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        //生成交易流水号，即订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //加上销量
        itemService.increaseSales(itemId, amount);

        /*
            优化—事务型消息：异步更新库存，将发送消息从扣减redis里面独立出来，
            在入库、销量增加后再发送消息。
               boolean mqResult=itemService.asyncDecreaseStock(itemId,amount);
               if(!mqResult){
                    //回滚redis 库存
                    itemService.increaseStock(itemId,amount);
                    throw new BizException(EmBizError.MQ_SEND_FAIL);
        */


        //设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null)
            throw new BizException(EmBizError.UNKNOWN_ERROR);
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);

        /*
            能解决提交后的问题，但是如果消息发送失败，则无能为力
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
                    if (!mqResult) {
                        //回滚redis 库存
                        itemService.increaseStock(itemId, amount);
                        throw new BizException(EmBizError.MQ_SEND_FAIL);
                    }
                }
            });
         */

        //4. 返回前端
        return orderModel;
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel) {
        if (orderModel == null) return null;
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generateOrderNo() {
        //订单号为16位
        StringBuilder stringBuilder = new StringBuilder();
        //前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);
        //中间6位为自增序列
        int sequence = 0;
        //获取当前Sequence
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        //获得当前的值
        sequence = sequenceDO.getCurrentValue();
        //设置下一次的值
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        //保存到数据库
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        //转成换String，用于拼接
        String seqStr = String.valueOf(sequence);
        //不足的位数，用0填充
        for (int i = 0; i < 6 - seqStr.length(); i++) {
            stringBuilder.append(0);
        }
        stringBuilder.append(seqStr);
        //最后2位为分库分表为，暂时写死
        stringBuilder.append("00");
        return stringBuilder.toString();
    }
}
