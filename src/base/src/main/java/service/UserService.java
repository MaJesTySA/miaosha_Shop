package service;
import error.BizException;
import service.model.UserModel;

public interface UserService {
    UserModel getUserById(Integer id);
    void register(UserModel userModel) throws BizException;
    //传入的密码是加密后的密码
    UserModel validateLogin(String telphone, String encrptPassword) throws BizException;
}
