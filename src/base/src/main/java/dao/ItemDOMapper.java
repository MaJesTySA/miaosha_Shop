package dao;

import org.apache.ibatis.annotations.Param;
import dataobject.ItemDO;

import java.util.List;

public interface ItemDOMapper {
    List<ItemDO> listItem();
    //增加销量
    int increaseSales(@Param("id") Integer id, @Param("amount") Integer amount);
    int deleteByPrimaryKey(Integer id);
    int insert(ItemDO record);
    int insertSelective(ItemDO record);
    ItemDO selectByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(ItemDO record);
    int updateByPrimaryKey(ItemDO record);
}