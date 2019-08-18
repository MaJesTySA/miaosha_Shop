package controller;

import com.alibaba.druid.util.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import controller.vo.UserVO;
import error.BizException;
import error.EmBizError;
import response.CommonReturnType;
import service.UserService;
import service.model.UserModel;
import sun.misc.BASE64Encoder;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

@Controller("user")
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class UserController extends BaseController {
    @Autowired
    private UserService userService;
    @Autowired
    private HttpServletRequest httpServletRequest;

    //用户注册接口
    @RequestMapping(value = "/register",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone")String telphone,
                                     @RequestParam(name = "otpCode")String otpCode,
                                     @RequestParam(name = "name")String name,
                                     @RequestParam(name = "gender")Integer gender,
                                     @RequestParam(name = "age")Integer age,
                                     @RequestParam(name="password")String password) throws BizException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //验证手机号和对应的otpCode相符合
        String inSessionOtpCode=(String)this.httpServletRequest.getSession().getAttribute(telphone);
        //工具类的equals已经进行了判空的处理
        if(!StringUtils.equals(otpCode,inSessionOtpCode)){
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR,"短信验证码不符合！");
        }
        //用户注册流程
        UserModel userModel=new UserModel();
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));
        userModel.setAge(age);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(this.EncodeByMD5(password));
        userService.register(userModel);
        return CommonReturnType.create(null);
    }

    //用户登录接口
    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name = "telphone")String telphone,
                                  @RequestParam(name = "password")String password) throws BizException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //入参校验
        if(org.apache.commons.lang3.StringUtils.isEmpty(telphone)|| org.apache.commons.lang3.StringUtils.isEmpty(password))
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR);
        UserModel userModel=userService.validateLogin(telphone,this.EncodeByMD5(password));
        //没有任何异常，则加入到用户登录成功的session内。这里不用分布式的处理方式。
        this.httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
        this.httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);
        return CommonReturnType.create(null);
    }

    //用户获取otp短信接口
    @RequestMapping(value = "/getOtp",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name="telphone")String telphone){
        //需要按照一定规则生成OPT验证码
        Random random=new Random();
        int randomInt=random.nextInt(99999);
        randomInt+=10000;
        String optCode=String.valueOf(randomInt);
        //将验证码与用户手机号进行关联，这里使用HttpSession
        httpServletRequest.getSession().setAttribute(telphone,optCode);
        //将OPT验证码通过短信通道发送给用户，省略
        System.out.println("telphone="+telphone+"& otpCode="+optCode);
        return CommonReturnType.create(null);
    }

    //获取用户接口
    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name="id")Integer id) throws BizException {
        UserModel userModel= userService.getUserById(id);
        if(userModel==null) {
            throw new BizException(EmBizError.USER_NOT_EXIST);
        }
        UserVO userVO=convertFromModel(userModel);
        return CommonReturnType.create(userVO);
    }

    //Model对象转VO对象
    private UserVO convertFromModel(UserModel userModel){
        if(userModel==null)return null;
        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }

    //加密密码
    private String EncodeByMD5(String str) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        MessageDigest md5=MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder=new BASE64Encoder();
        return base64Encoder.encode(md5.digest(str.getBytes("utf-8")));
    }
}
