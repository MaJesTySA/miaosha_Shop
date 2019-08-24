package person.sa.service;

import person.sa.error.BizException;
import person.sa.service.model.OrderModel;

public interface OrderService {
    //1.通过前端url上传过来秒杀活动Id，然后下单接口内校验对应id是否属于对应商品且活动已开始
    //2.直接在下单接口内，判断对应的商品是否存在秒杀活动，若存在则以秒杀价格下单。
    OrderModel createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId) throws BizException;

}
