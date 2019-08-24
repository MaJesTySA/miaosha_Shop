package person.sa.service.impl;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import person.sa.dao.ItemDOMapper;
import person.sa.dao.ItemStockDOMapper;
import person.sa.dao.StockLogDOMapper;
import person.sa.dataobject.ItemDO;
import person.sa.dataobject.ItemStockDO;
import person.sa.dataobject.StockLogDO;
import person.sa.error.BizException;
import person.sa.error.EmBizError;
import person.sa.mq.MqProducer;
import person.sa.service.ItemService;
import person.sa.service.PromoService;
import person.sa.service.model.ItemModel;
import person.sa.service.model.PromoModel;
import person.sa.validator.ValidationResult;
import person.sa.validator.ValidatorImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    private ItemDOMapper itemDOMapper;
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @Autowired
    private PromoService promoService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer mqProducer;

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BizException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()) {
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //转化ItemModel变成DataObject
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);
        //写入数据库
        itemDOMapper.insertSelective(itemDO);
        //得到生成的主键，并将主键一并写入到itemStock表
        itemModel.setId(itemDO.getId());
        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);
        itemStockDOMapper.insertSelective(itemStockDO);
        //返回创建完成的对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> list = itemDOMapper.listItem();
        List<ItemModel> itemModelList = list.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            return this.convertModelFromDataObject(itemDO, itemStockDO);
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {
        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
        if (itemDO == null) return null;
        //操作获得库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
        //将dataObj转换成Model
        ItemModel itemModel = convertModelFromDataObject(itemDO, itemStockDO);
        //获取商品的活动信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if (promoModel != null && promoModel.getStatus() != 3) {
            itemModel.setPromoModel(promoModel);
        }
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) {
        /*
            老方法，直接在数据库减
            int affectedRow=itemStockDOMapper.decreaseStock(itemId,amount);
        */

        long affectedRow = redisTemplate.opsForValue().
                increment("promo_item_stock_" + itemId, amount.intValue() * -1);
        /*
            事务性消息更改后的版本，在整个下单业务执行完毕后，再发送消息。
            考虑售罄的情况
            if(affectedRow>=0){
                boolean mqResult=mqProducer.asyncReduceStock(itemId,amount);
                if(!mqResult){
                    //消息发送失败，回滚redis
                    redisTemplate.opsForValue().
                        increment("promo_item_stock_"+itemId,amount.intValue());
                    return false;
                }
        */

        if (affectedRow > 0) {
            return true;
        } else if (affectedRow == 0) {
//            redisTemplate.opsForValue().
//                    increment("promo_item_stock_"+itemId,amount.intValue());
            //打上售罄标识
            redisTemplate.opsForValue().set("promo_item_stock_invalid_" + itemId, "true");
            return true;
        } else {
            increaseStock(itemId, amount);
            return false;
        }
    }

    @Override
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

    @Override
    public boolean increaseStock(Integer itemId, Integer amount) {
        redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
        return true;
    }

    @Override
    public boolean asyncDecreaseStock(Integer itemId, Integer amount) {
        return mqProducer.asyncReduceStock(itemId, amount);
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId, amount);
    }

    private ItemDO convertItemDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) return null;
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel, itemDO);
        itemDO.setPrice(itemModel.getPrice().doubleValue());
        return itemDO;
    }

    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel) {
        if (itemModel == null) return null;
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }

    private ItemModel convertModelFromDataObject(ItemDO itemDO, ItemStockDO itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        itemModel.setPrice(new BigDecimal(itemDO.getPrice()));
        itemModel.setStock(itemStockDO.getStock());
        return itemModel;
    }

    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        ItemModel itemModel = (ItemModel) redisTemplate.opsForValue().get("item_validate_" + id);
        if (itemModel == null) {
            itemModel = this.getItemById(id);
            redisTemplate.opsForValue().set("item_validate_" + id, itemModel);
            redisTemplate.expire("item_validate_" + id, 10, TimeUnit.MINUTES);
        }
        return itemModel;
    }
}
