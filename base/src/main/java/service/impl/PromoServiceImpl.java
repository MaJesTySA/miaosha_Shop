package service.impl;

import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import dao.PromoDOMapper;
import dataobject.PromoDO;
import service.PromoService;
import service.model.PromoModel;
import java.math.BigDecimal;


@Service
public class PromoServiceImpl implements PromoService {
    @Autowired
    private PromoDOMapper promoDOMapper;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取商品的秒杀活动信息
        PromoDO promoDO=promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel=convertFromDataObj(promoDO);
        if(promoModel==null) return null;
        //判断当前时间秒杀活动是还未开始还是即将开始
        DateTime now=new DateTime();
        if(promoModel.getStartDate().isAfterNow()) {
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    private PromoModel convertFromDataObj(PromoDO promoDO){
        if (promoDO==null) return null;
        PromoModel promoModel=new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        //价格DO是double而Model是BigDecimal，要转一下
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        //还有日期也要转一下
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
