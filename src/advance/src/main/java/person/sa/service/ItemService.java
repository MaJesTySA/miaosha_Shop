package person.sa.service;

import person.sa.error.BizException;
import person.sa.service.model.ItemModel;

import java.util.List;

public interface ItemService {
    //创建商品
    ItemModel createItem(ItemModel itemModel) throws BizException;

    //列表浏览
    List<ItemModel> listItem();

    //详情浏览
    ItemModel getItemById(Integer id);

    //库存扣减
    boolean decreaseStock(Integer itemId, Integer amount);

    //库存增加
    boolean increaseStock(Integer itemId, Integer amount);

    //增加销量
    void increaseSales(Integer itemId, Integer amount);

    //优化：Item及promo model缓存模型
    // 验证item及promo是否有效
    ItemModel getItemByIdInCache(Integer id);

    //异步更新库存
    boolean asyncDecreaseStock(Integer itemId, Integer amount);

    //初始化库存流水
    String initStockLog(Integer itemId, Integer amount);
}


