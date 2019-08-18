package error;
//包装器业务异常类实现
public class BizException extends Exception implements CommonError{
    private CommonError commonError;

    //直接接受EmBizError的传参，用于构造业务异常
    public BizException(CommonError commonError){
        super();
        this.commonError=commonError;
    }

    //接受自定义errMsg构造义务异常
    public BizException(CommonError commonError,String errMsg){
        super();
        this.commonError=commonError;
        this.commonError.setErrMsg(errMsg);
    }
    @Override
    public int getErrCode() {
        return this.commonError.getErrCode();
    }
    @Override
    public String getErrMsg() {
        return this.commonError.getErrMsg();
    }
    @Override
    public CommonError setErrMsg(String errMsg) {
        this.commonError.setErrMsg(errMsg);
        return this;
    }
}
