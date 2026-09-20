package cn.miniants.platform.admin.dto;

import java.time.LocalDateTime;

public class ResourceVo {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer type;
    private String path;
    private Integer sortNo;
    private Integer status;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 没配前端路由字段时为 null，不是空对象——前端据此区分「没配」和「配成了空」。 */
    private ResourceMetaDto meta;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
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

    /** 旧管理端权限树读 {@code title}；与 {@link #name} 同一值。 */
    public String getTitle() {
        return name;
    }

    /** Element Plus 树默认 label 字段。 */
    public String getLabel() {
        return name;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public ResourceMetaDto getMeta() {
        return meta;
    }

    public void setMeta(ResourceMetaDto meta) {
        this.meta = meta;
    }
}
