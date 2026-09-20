package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import cn.miniants.platform.data.query.QueryHidden;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_oauth_client")
public class OauthClient extends LogicDeleteEntity {

    private String clientId;

    @QueryHidden
    private String clientSecret;

    private String clientName;
    private String grantTypes;
    private String scopes;
    private Integer accessTokenTtl;
    private Integer refreshTokenTtl;
    private Integer status;
    private String remark;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
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
}
