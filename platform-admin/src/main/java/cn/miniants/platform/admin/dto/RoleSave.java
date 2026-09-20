package cn.miniants.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public class RoleSave {

    private Long id;

    @Size(max = 64, message = "角色编码过长")
    private String code;

    @Size(max = 64, message = "角色名称过长")
    private String name;

    @Size(max = 255, message = "备注过长")
    private String remark;

    private Integer sortNo;

    @Min(value = 0, message = "状态取值无效")
    @Max(value = 1, message = "状态取值无效")
    private Integer status;

    private Long version;
    private List<Long> resourceIds;

    /** 不传就不动已有的数据范围；传了整体覆盖。 */
    private DataScopeDto dataScope;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<Long> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(List<Long> resourceIds) {
        this.resourceIds = resourceIds;
    }

    public DataScopeDto getDataScope() {
        return dataScope;
    }

    public void setDataScope(DataScopeDto dataScope) {
        this.dataScope = dataScope;
    }
}
