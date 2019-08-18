package dao;


import dataobject.UserPasswordDO;

public interface UserPasswordDOMapper {
    int deleteByPrimaryKey(Integer id);
    int insert(UserPasswordDO record);
    int insertSelective(UserPasswordDO record);
    int updateByPrimaryKeySelective(UserPasswordDO record);
    int updateByPrimaryKey(UserPasswordDO record);
    UserPasswordDO selectByPrimaryKey(Integer id);
    UserPasswordDO selectByUserId(Integer id);
}