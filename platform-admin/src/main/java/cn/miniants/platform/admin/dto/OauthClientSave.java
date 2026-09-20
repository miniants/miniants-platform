package cn.miniants.platform.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class OauthClientSave {

    private Long id;

    @Size(max = 64, message = "clientId 过长")
    private String clientId;

    @Size(max = 128, message = "客户端名称过长")
    private String clientName;

    @Size(max = 512, message = "grantTypes 过长")
    private String grantTypes;

    @Size(max = 512, message = "scopes 过长")
    private String scopes;

    @Min(value = 1, message = "accessTokenTtl 无效")
    private Integer accessTokenTtl;

    @Min(value = 1, message = "refreshTokenTtl 无效")
    private Integer refreshTokenTtl;

    @Min(value = 0, message = "状态取值无效")
    @Max(value = 1, message = "状态取值无效")
    private Integer status;

    @Size(max = 255, message = "备注过长")
    private String remark;

    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(String grantTypes) {
        this.grantTypes = grantTypes;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Integer getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Integer accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Integer getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Integer refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
