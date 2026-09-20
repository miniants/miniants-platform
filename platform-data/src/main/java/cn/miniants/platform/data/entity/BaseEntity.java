package cn.miniants.platform.data.entity;

import cn.miniants.platform.core.json.PlatformDateTimeFormats;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class BaseEntity extends SuperEntity {

    @TableField(fill = FieldFill.INSERT)
    protected Long createId;

    @TableField(fill = FieldFill.INSERT)
    protected String createBy;

    @JsonFormat(pattern = PlatformDateTimeFormats.DATE_TIME_MINUTES)
    @TableField(fill = FieldFill.INSERT)
    protected LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    protected String updateBy;

    @JsonFormat(pattern = PlatformDateTimeFormats.DATE_TIME_MINUTES)
    @TableField(fill = FieldFill.UPDATE)
    protected LocalDateTime updateTime;

    public Long getCreateId() {
        return createId;
    }

    public void setCreateId(Long createId) {
        this.createId = createId;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
