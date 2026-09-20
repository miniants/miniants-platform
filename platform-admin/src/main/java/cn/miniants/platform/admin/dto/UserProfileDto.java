package cn.miniants.platform.admin.dto;

/**
 * 用户资料。挂在 {@link UserVo} / {@link UserSave} 的 {@code profile} 下。
 *
 * <p>不含 verified 标记：手机 / 邮箱是否已验证由验证流程写，不该让管理员在表单里随手勾。
 */
public class UserProfileDto {

    private String realName;
    private String nickName;
    private String avatar;
    private String sex;
    private String phone;
    private String email;

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
