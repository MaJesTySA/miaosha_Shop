package service.impl;

import dao.UserDOMapper;
import dao.UserPasswordDOMapper;
import dataobject.UserDO;
import dataobject.UserPasswordDO;
import error.BizException;
import error.EmBizError;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import service.UserService;
import service.model.UserModel;
import validator.ValidationResult;
import validator.ValidatorImpl;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;
    @Autowired
    private ValidatorImpl validator;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO=userDOMapper.selectByPrimaryKey(id);
        if(userDO==null) return null;
        UserPasswordDO userPasswordDO=
                userPasswordDOMapper.selectByUserId(userDO.getId());
        return convertFromDataObj(userDO,userPasswordDO);
    }

    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BizException {
        //通过手机获取用户信息
        UserDO userDO=userDOMapper.selectByTelphone(telphone);
        if(userDO==null){
            throw new BizException(EmBizError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO=userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel=convertFromDataObj(userDO,userPasswordDO);
        //比对用户信息的密码与传输进来的密码是否匹配
        if(!StringUtils.equals(encrptPassword,userModel.getEncrptPassword())){
            throw new BizException(EmBizError.USER_LOGIN_FAIL);
        }
        return userModel;
    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BizException {
        if(userModel==null){
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR);
        }
        ValidationResult result=validator.validate(userModel);
        if(result.isHasErrors()){
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        //Model转成DataObject
        UserDO userDO=convertFromModel(userModel);
        //这里使用insertSelective，不使用insert方法
        try{
            userDOMapper.insertSelective(userDO);
        }catch (DuplicateKeyException e){
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"手机号已被注册");
        }
        //得到主键
        userModel.setId(userDO.getId());
        //处理Password
        UserPasswordDO userPasswordDO= convertPasswordFromModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
    }

    private UserDO convertFromModel(UserModel userModel){
        if(userModel==null) return null;
        UserDO userDO=new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }

    private UserPasswordDO convertPasswordFromModel(UserModel userModel){
        if(userModel==null) return null;
        UserPasswordDO userPasswordDO=new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    private UserModel convertFromDataObj(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO==null) return null;
        UserModel userModel=new UserModel();
        BeanUtils.copyProperties(userDO,userModel);
        if(userPasswordDO!=null){
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        }
        return userModel;
    }
}
