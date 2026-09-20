package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_external_identity")
public class ExternalIdentityEntity extends LogicDeleteEntity {

    private String provider;
    private String subject;
    private Long personId;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getPersonId() {
        return personId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }
}
