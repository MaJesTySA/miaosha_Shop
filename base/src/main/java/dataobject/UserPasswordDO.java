package dataobject;

public class UserPasswordDO {
    private Integer id;
    private String encrptPassword;
    private Integer userId;

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getEncrptPassword() {
        return encrptPassword;
    }
    public void setEncrptPassword(String encrptPassword) {
        this.encrptPassword = encrptPassword == null ? null : encrptPassword.trim();
    }
    public Integer getUserId() {
        return userId;
    }
    public void setUserId(Integer userId) {
        this.userId = userId;
    }
}