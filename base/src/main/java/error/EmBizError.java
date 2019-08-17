package error;

public enum EmBizError implements CommonError {
    //通用错误类型10000
    PARAMETER_VALIDATION_ERROR(100001,"参数不合法"),
    UNKNOWN_ERROR(100002,"未知错误"),
    //用户相关错误20000
    USER_NOT_EXIST(20001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"用户手机或密码不正确"),
    USER_NOT_LOGIN(20003,"用户还未登录"),
    //交易相关错误30000
    STOCK_NOT_ENOUGH(30001,"库存不足");

    private int errCode;
    private String errMsg;
    private EmBizError(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    @Override
    public int getErrCode() {
        return this.errCode;
    }
    @Override
    public String getErrMsg() {
        return this.errMsg;
    }
    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg=errMsg;
        return this;
    }
}
