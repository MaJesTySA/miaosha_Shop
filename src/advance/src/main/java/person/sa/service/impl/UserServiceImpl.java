package person.sa.service.impl;

import com.sun.xml.internal.messaging.saaj.packaging.mime.util.BEncoderStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import person.sa.controller.vo.UserVO;
import person.sa.dao.UserDOMapper;
import person.sa.dao.UserPasswordDOMapper;
import person.sa.dataobject.UserDO;
import person.sa.dataobject.UserPasswordDO;
import person.sa.error.BizException;
import person.sa.error.EmBizError;
import person.sa.service.UserService;
import person.sa.service.model.UserModel;
import person.sa.validator.ValidationResult;
import person.sa.validator.ValidatorImpl;

import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;
    @Autowired
    private ValidatorImpl validator;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public UserModel getUserById(Integer id) {
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if (userDO == null) return null;
        UserPasswordDO userPasswordDO =
                userPasswordDOMapper.selectByUserId(userDO.getId());
        return convertFromDataObj(userDO, userPasswordDO);
    }

    @Override
    public UserModel validateLogin(String telphone, String encrptPassword) throws BizException {
        //通过手机获取用户信息
        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        if (userDO == null) {
            throw new BizException(EmBizError.USER_LOGIN_FAIL);
        }
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = convertFromDataObj(userDO, userPasswordDO);
        //比对用户信息的密码与传输进来的密码是否匹配
        if (!StringUtils.equals(encrptPassword, userModel.getEncrptPassword())) {
            throw new BizException(EmBizError.USER_LOGIN_FAIL);
        }
        return userModel;
    }

    @Override
    @Transactional
    public void register(UserModel userModel) throws BizException {
        if (userModel == null) {
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR);
        }
//        if(StringUtils.isEmpty(userModel.getName())
//                ||userModel.getGender()==null
//                ||userModel.getAge()==null
//                ||StringUtils.isEmpty(userModel.getTelphone())){
//            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR);
//        }
        ValidationResult result = validator.validate(userModel);
        if (result.isHasErrors()) {
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
        }
        //Model转成DataObject
        UserDO userDO = convertFromModel(userModel);
        //这里使用insertSelective，不使用insert方法
        try {
            userDOMapper.insertSelective(userDO);
        } catch (DuplicateKeyException e) {
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "手机号已被注册");
        }

        userModel.setId(userDO.getId());
        //处理Password
        UserPasswordDO userPasswordDO = convertPasswordFormModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
    }

    private UserDO convertFromModel(UserModel userModel) {
        if (userModel == null) return null;
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel, userDO);
        return userDO;
    }

    private UserPasswordDO convertPasswordFormModel(UserModel userModel) {
        if (userModel == null) return null;
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }

    private UserModel convertFromDataObj(UserDO userDO, UserPasswordDO userPasswordDO) {
        if (userDO == null) return null;
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO, userModel);
        if (userPasswordDO != null) {
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        }
        return userModel;
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_" + id);
        if (userModel == null) {
            userModel = this.getUserById(id);
            redisTemplate.opsForValue().set("user_validate_" + id, userModel);
            redisTemplate.expire("user_validate_" + id, 10, TimeUnit.MINUTES);
        }
        return userModel;
    }
}
