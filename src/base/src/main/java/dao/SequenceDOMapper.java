package dao;

import dataobject.SequenceDO;

public interface SequenceDOMapper {
    SequenceDO getSequenceByName(String name);
    int deleteByPrimaryKey(String name);
    int insert(SequenceDO record);
    int insertSelective(SequenceDO record);
    SequenceDO selectByPrimaryKey(String name);
    int updateByPrimaryKeySelective(SequenceDO record);
    int updateByPrimaryKey(SequenceDO record);
}