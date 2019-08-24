package person.sa.controller;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import person.sa.error.BizException;
import person.sa.error.EmBizError;
import person.sa.mq.MqProducer;
import person.sa.response.CommonReturnType;
import person.sa.service.ItemService;
import person.sa.service.OrderService;
import person.sa.service.PromoService;
import person.sa.service.model.UserModel;
import person.sa.util.CodeUtil;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private ItemService itemService;
    @Autowired
    private PromoService promoService;
    private ExecutorService executorService;
    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer mqProducer;
    private RateLimiter orderCreateRateLimiter;

    @PostConstruct
    public void init() {
        //20个线程的线程池
        executorService = Executors.newFixedThreadPool(20);
        orderCreateRateLimiter = RateLimiter.create(200);
    }

    //生成验证码
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public void generateVerifyCode(HttpServletResponse response) throws BizException, IOException {
        //获取根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BizException(EmBizError.USER_NOT_LOGIN, "用户还未登录，不能生成验证码");
        }
        //获取用户登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BizException(EmBizError.USER_NOT_LOGIN, "登录过期，不能生成验证");
        }
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("verify_code_" + userModel.getId(), map.get("code"));
        redisTemplate.expire("verify_code_" + userModel.getId(), 10, TimeUnit.MINUTES);
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }

    //生成秒杀令牌
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateToken(@RequestParam(name = "itemId") Integer itemId,
                                          @RequestParam(name = "promoId") Integer promoId,
                                          @RequestParam(name = "verifyCode") String verifyCode) throws BizException {
        //获取根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token))
            throw new BizException(EmBizError.USER_NOT_LOGIN, "用户还未登录，不能下单");

        //获取用户登录信息
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null)
            throw new BizException(EmBizError.USER_NOT_LOGIN, "登录过期，请重新登录");

        //验证验证码的有效性
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        if (StringUtils.isEmpty(redisVerifyCode))
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "请求非法");
        if (!redisVerifyCode.equalsIgnoreCase(verifyCode))
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "请求非法");
        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());
        if (promoToken == null)
            throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        return CommonReturnType.create(promoToken);
    }


    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "promoId", required = false) Integer promoId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoToken", required = false) String promoToken) throws BizException {

        /*  使用sessionId的方式 验证用户信息
            Boolean isLogin=(Boolean)httpServletRequest.getSession().getAttribute("IS_LOGIN");
            if(isLogin==null||!isLogin.booleanValue())
                throw new BizException(EmBizError.USER_NOT_LOGIN,"用户还未登录，不能下单");
            UserModel userModel=(UserModel)httpServletRequest.getSession().getAttribute("LOGIN_USER");
        */

        //限流
        if (!orderCreateRateLimiter.tryAcquire())
            throw new BizException(EmBizError.RATELIMIT);

        //使用Token的方式
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)) {
            throw new BizException(EmBizError.USER_NOT_LOGIN, "用户还未登录，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null) {
            throw new BizException(EmBizError.USER_NOT_LOGIN, "登录过期，请重新登录");
        }

        //校验秒杀令牌是否正确
        if (promoId != null) {
            String inRedisPromoToken = (String) redisTemplate.opsForValue().
                    get("promo_token_" + promoId + "_userid_" + userModel.getId() + "_itemid_" + itemId);
            if (inRedisPromoToken == null) {
                throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "令牌校验失败");
            }
            if (!StringUtils.equals(promoToken, inRedisPromoToken)) {
                throw new BizException(EmBizError.PARAMETER_VALIDATION_ERROR, "令牌校验失败");
            }
        }

        /*是否售罄 已经整合在秒杀令牌生成中
        if (redisTemplate.hasKey("promo_item_stock_invalid_"+itemId))
            throw new BizException(EmBizError.STOCK_NOT_ENOUGH);
        */

        //同步调用线程池的submit方法
        //拥塞窗口为20的等待队列，用来泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态
                String stockLogId = itemService.initStockLog(itemId, amount);
                //非消息事务的处理方式
                //orderService.createOrder(userModel.getId(),itemId,promoId,amount);
                //消息事务处理方法
                if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
                    throw new BizException(EmBizError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new BizException(EmBizError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new BizException(EmBizError.UNKNOWN_ERROR);
        }
        return CommonReturnType.create(null);
    }
}
