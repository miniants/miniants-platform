package cn.miniants.platform.admin.dto;

/**
 * 资源的前端路由字段。挂在 {@link ResourceVo} / {@link ResourceSave} 的 {@code meta} 下，
 * 不平铺进主体——这些字段属于前端框架的路由约定，不是资源本身。
 */
public class ResourceMetaDto {

    private String routeName;
    private String component;
    private String redirect;
    private String icon;
    private String link;
    private Boolean hide;
    private Boolean keepAlive;
    private Boolean affix;
    private Boolean iframe;

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
}
