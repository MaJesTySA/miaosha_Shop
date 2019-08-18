package validator;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class ValidationResult {
    //校验结果是否有错
    private boolean hasErrors=false;
    //存放错误信息的Map
    private Map<String,String> errorMsgMap=new HashMap<>();
    //实现通过格式化字符串信息获取错误结果的msg方法
    public String getErrMsg(){
        return StringUtils.join(errorMsgMap.values().toArray(),",");
    }
    public boolean isHasErrors() {
        return hasErrors;
    }
    public void setHasErrors(boolean hasErrors) {
        this.hasErrors = hasErrors;
    }
    public Map<String, String> getErrorMsgMap() {
        return errorMsgMap;
    }
    public void setErrorMsgMap(Map<String, String> errorMsgMap) {
        this.errorMsgMap = errorMsgMap;
    }
}
