package dao;

import dataobject.ItemStockDO;
import org.apache.ibatis.annotations.Param;

public interface ItemStockDOMapper {
    ItemStockDO selectByItemId(Integer id);
    int decreaseStock(@Param("itemId") Integer itemId,
                      @Param("amount") Integer amount);
    int deleteByPrimaryKey(Integer id);
    int insert(ItemStockDO record);
    int insertSelective(ItemStockDO record);
    ItemStockDO selectByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(ItemStockDO record);
    int updateByPrimaryKey(ItemStockDO record);
}