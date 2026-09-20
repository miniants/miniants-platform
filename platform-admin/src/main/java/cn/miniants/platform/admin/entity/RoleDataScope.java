package cn.miniants.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 角色的数据范围。主键即 {@code role_id}，没这行等于不限制。
 */
@TableName("sys_role_data_scope")
public class RoleDataScope {

    /** 0 全部。 */
    public static final int SCOPE_ALL = 0;

    /** 1 自定义：范围由 {@link #scopeValue} 给出。 */
    public static final int SCOPE_CUSTOM = 1;

    /** 2 用户已绑组织。 */
    public static final int SCOPE_USER_BOUND = 2;

    /** 4 仅本人。3 预留给组织及下级，避免与存量取值冲突。 */
    public static final int SCOPE_SELF = 4;

    @TableId(type = IdType.INPUT)
    private Long roleId;

    private Integer scopeType;

    /** {@link #SCOPE_CUSTOM} 时的组织 ID，逗号分隔。 */
    private String scopeValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getScopeType() {
        return scopeType;
    }

    public void setScopeType(Integer scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeValue() {
        return scopeValue;
    }

    public void setScopeValue(String scopeValue) {
        this.scopeValue = scopeValue;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
