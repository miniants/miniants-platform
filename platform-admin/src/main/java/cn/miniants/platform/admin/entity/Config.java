package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_config")
public class Config extends LogicDeleteEntity {

    private String category;
    private String configKey;
    private String title;
    private String configValue;
    private String remark;
    private Integer sortNo;
    private Integer status;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
}
