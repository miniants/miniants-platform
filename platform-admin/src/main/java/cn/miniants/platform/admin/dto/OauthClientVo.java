package cn.miniants.platform.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

public class OauthClientVo {

    private Long id;
    private String clientId;
    private String clientName;
    private String grantTypes;
    private String scopes;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Integer status;
    private String remark;
    private boolean secretConfigured;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String plaintextSecret;

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

    public boolean isSecretConfigured() {
        return secretConfigured;
    }

    public void setSecretConfigured(boolean secretConfigured) {
        this.secretConfigured = secretConfigured;
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

    public String getPlaintextSecret() {
        return plaintextSecret;
    }

    public void setPlaintextSecret(String plaintextSecret) {
        this.plaintextSecret = plaintextSecret;
    }
}
