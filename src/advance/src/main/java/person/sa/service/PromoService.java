package person.sa.service;

import person.sa.service.model.PromoModel;

public interface PromoService {
    //获取即将进行或正在进行的商品活动
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //生成秒杀令牌
    String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId);
}
