package cn.miniants.platform.admin.dto;

public class ResourceSave {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer type;
    private String path;
    private Integer sortNo;
    private Integer status;
    private Long version;

    /** 不传就不动已有的 meta 行；传了整体覆盖。 */
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

    public ResourceMetaDto getMeta() {
        return meta;
    }

    public void setMeta(ResourceMetaDto meta) {
        this.meta = meta;
    }
}
