package cn.miniants.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 资源的前端路由字段。主键即 {@code resource_id}，一个资源至多一行。
 *
 * <p>这些字段的形状来自前端框架的路由约定，所以不进 {@code sys_resource} 主表。
 */
@TableName("sys_resource_meta")
public class ResourceMeta {

    @TableId(type = IdType.INPUT)
    private Long resourceId;

    /** 前端路由 name，与主表 name（展示名）不是一回事。 */
    private String routeName;

    private String component;
    private String redirect;
    private String icon;

    /** 外链地址；非空即为外链。 */
    private String link;

    private Boolean hide;
    private Boolean keepAlive;
    private Boolean affix;
    private Boolean iframe;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public Boolean getHide() {
        return hide;
    }

    public void setHide(Boolean hide) {
        this.hide = hide;
    }

    public Boolean getKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(Boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public Boolean getAffix() {
        return affix;
    }

    public void setAffix(Boolean affix) {
        this.affix = affix;
    }

    public Boolean getIframe() {
        return iframe;
    }

    public void setIframe(Boolean iframe) {
        this.iframe = iframe;
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
}
