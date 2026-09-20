package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import cn.miniants.platform.data.query.QueryHidden;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user")
public class User extends LogicDeleteEntity {

    private String username;

    @QueryHidden
    private String password;

    private String displayName;
    private Integer status;
    private Integer sysAdmin;
    private Long personId;

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

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }
}
