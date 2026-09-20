package cn.miniants.platform.admin.entity;

import cn.miniants.platform.data.entity.LogicDeleteEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_role")
public class Role extends LogicDeleteEntity {

    private String code;
    private String name;
    private String remark;
    private Integer sortNo;
    private Integer status;

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
