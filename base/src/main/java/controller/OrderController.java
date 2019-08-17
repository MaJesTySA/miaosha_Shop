package controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import error.BizException;
import error.EmBizError;
import response.CommonReturnType;
import service.OrderService;
import service.model.UserModel;
import javax.servlet.http.HttpServletRequest;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"},allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private HttpServletRequest httpServletRequest;

    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "promoId",required = false)Integer promoId,
                                        @RequestParam(name = "amount")Integer amount) throws BizException {
        Boolean isLogin=(Boolean)httpServletRequest.getSession().getAttribute("IS_LOGIN");
        if(isLogin==null||!isLogin.booleanValue())
            throw new BizException(EmBizError.USER_NOT_LOGIN,"用户还未登录，不能下单");
        //获取用户的登录信息
        UserModel userModel=(UserModel)httpServletRequest.getSession().getAttribute("LOGIN_USER");
        orderService.createOrder(userModel.getId(),itemId,promoId,amount);
        return CommonReturnType.create(null);
    }
}
