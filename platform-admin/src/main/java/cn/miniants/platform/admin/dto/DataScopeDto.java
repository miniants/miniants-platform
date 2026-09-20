package cn.miniants.platform.admin.dto;

/**
 * 角色的数据范围。取值见 {@link cn.miniants.platform.admin.entity.RoleDataScope}。
 */
public class DataScopeDto {

    private Integer scopeType;

    /** {@code scopeType=1} 时的组织 ID，逗号分隔。 */
    private String scopeValue;

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
}
