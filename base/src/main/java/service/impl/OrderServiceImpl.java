package service.impl;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import dao.OrderDOMapper;
import dao.SequenceDOMapper;
import dataobject.OrderDO;
import dataobject.SequenceDO;
import error.BizException;
import error.EmBizError;
import service.ItemService;
import service.OrderService;
import service.UserService;
import service.model.ItemModel;
import service.model.OrderModel;
import service.model.UserModel;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId,Integer promoId, Integer amount) throws BizException {
        //1. 校验下单状态。下单商品是否存在，用户是否合法，购买数量是否正确
        ItemModel itemModel=itemService.getItemById(itemId);
        if(itemModel==null)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        UserModel userModel=userService.getUserById(userId);
        if(userModel==null)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
        if(amount<=0||amount>99)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"数量信息不存在");
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
        //2. 落单减库存 or 支付减库存。这里是落单减库存
        boolean result=itemService.decreaseStock(itemId,amount);
        if(!result)
            throw new BizException(EmBizError.STOCK_NOT_ENOUGH);
        //3. 订单入库
        OrderModel orderModel=new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId!=null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        //生成交易流水号，即订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO=convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        //加上销量
        itemService.increaseSales(itemId,amount);
        //4. 返回前端
        return orderModel;
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel==null) return null;
        OrderDO orderDO=new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private String generateOrderNo(){
        //订单号为16位
        StringBuilder stringBuilder=new StringBuilder();
        //前8位为时间信息，年月日
        LocalDateTime now=LocalDateTime.now();
        String nowDate=now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowDate);
        //中间6位为自增序列
        int sequence=0;
            //获取当前Sequence
            SequenceDO sequenceDO=sequenceDOMapper.getSequenceByName("order_info");
            //获得当前的值
            sequence=sequenceDO.getCurrentValue();
            //设置下一次的值
            sequenceDO.setCurrentValue(sequenceDO.getCurrentValue()+sequenceDO.getStep());
            //保存到数据库
            sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        //转成换String，用于拼接
        String seqStr=String.valueOf(sequence);
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
