package cn.miniants.platform.data.entity;

import com.baomidou.mybatisplus.annotation.Version;

public class VersionedEntity extends BaseEntity {

    @Version
    protected Long version;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
