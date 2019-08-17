package dao;

import dataobject.UserDO;

public interface UserDOMapper {
    int deleteByPrimaryKey(Integer id);
    int insert(UserDO record);
    int insertSelective(UserDO record);
    int updateByPrimaryKeySelective(UserDO record);
    int updateByPrimaryKey(UserDO record);
    UserDO selectByPrimaryKey(Integer id);
    UserDO selectByTelphone(String telphone);
}