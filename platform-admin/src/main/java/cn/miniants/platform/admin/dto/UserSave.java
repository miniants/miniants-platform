package cn.miniants.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UserSave {

    private Long id;

    @Size(max = 64, message = "用户名过长")
    private String username;

    @Size(min = 6, max = 128, message = "密码长度须在 6-128 之间")
    private String password;

    @Size(max = 64, message = "显示名过长")
    private String displayName;

    @Min(value = 0, message = "状态取值无效")
    @Max(value = 1, message = "状态取值无效")
    private Integer status;

    @Min(value = 0, message = "sysAdmin 取值无效")
    @Max(value = 1, message = "sysAdmin 取值无效")
    private Integer sysAdmin;

    private Long version;
    private List<Long> roleIds;

    /** 不传就不动已有资料；传了整体覆盖。 */
    private UserProfileDto profile;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSysAdmin() {
        return sysAdmin;
    }

    public void setSysAdmin(Integer sysAdmin) {
        this.sysAdmin = sysAdmin;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }

    public UserProfileDto getProfile() {
        return profile;
    }

    public void setProfile(UserProfileDto profile) {
        this.profile = profile;
    }
}
