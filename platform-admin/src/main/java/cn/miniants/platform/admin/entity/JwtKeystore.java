package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import cn.miniants.platform.data.query.QueryHidden;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_jwt_keystore")
public class JwtKeystore extends LogicDeleteEntity {

    private String kid;
    private String algorithm;
    private String publicKey;

    @QueryHidden
    private String privateKeyCipher;

    private String status;
    private Integer isActive;
    private LocalDateTime activatedAt;
    private LocalDateTime retiredAt;

    public String getKid() {
        return kid;
    }

    public void setKid(String kid) {
        this.kid = kid;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKeyCipher() {
        return privateKeyCipher;
    }

    public void setPrivateKeyCipher(String privateKeyCipher) {
        this.privateKeyCipher = privateKeyCipher;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIsActive() {
        return isActive;
    }

    public void setIsActive(Integer isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getRetiredAt() {
        return retiredAt;
    }

    public void setRetiredAt(LocalDateTime retiredAt) {
        this.retiredAt = retiredAt;
    }
}
