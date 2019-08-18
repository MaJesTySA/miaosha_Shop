package dao;

import dataobject.PromoDO;

public interface PromoDOMapper {
    PromoDO selectByItemId(Integer itemId);
    int deleteByPrimaryKey(Integer id);
    int insert(PromoDO record);
    int insertSelective(PromoDO record);
    PromoDO selectByPrimaryKey(Integer id);
    int updateByPrimaryKeySelective(PromoDO record);
    int updateByPrimaryKey(PromoDO record);
}