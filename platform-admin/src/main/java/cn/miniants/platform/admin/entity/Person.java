package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_person")
public class Person extends LogicDeleteEntity {

    private String realName;
    private String idDocType;
    private String idLookupDigest;
    private String idCipher;
    private LocalDateTime verifiedAt;

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getIdDocType() {
        return idDocType;
    }

    public void setIdDocType(String idDocType) {
        this.idDocType = idDocType;
    }

    public String getIdLookupDigest() {
        return idLookupDigest;
    }

    public void setIdLookupDigest(String idLookupDigest) {
        this.idLookupDigest = idLookupDigest;
    }

    public String getIdCipher() {
        return idCipher;
    }

    public void setIdCipher(String idCipher) {
        this.idCipher = idCipher;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(LocalDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }
}
