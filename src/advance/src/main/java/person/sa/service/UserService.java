package person.sa.service;

import person.sa.error.BizException;
import person.sa.service.model.UserModel;

public interface UserService {
    UserModel getUserById(Integer id);

    void register(UserModel userModel) throws BizException;

    //传入的密码是加密后的密码
    UserModel validateLogin(String telphone, String encrptPassword) throws BizException;

    //优化：通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);
}
