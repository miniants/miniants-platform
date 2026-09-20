package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.dto.ResourceMetaDto;
import cn.miniants.platform.admin.entity.ResourceMeta;

public final class ResourceMetas {

    private ResourceMetas() {
    }

    /** 没有 meta 行时返回 null：前端据此区分「没配」和「配成了空」。 */
    public static ResourceMetaDto toDto(ResourceMeta row) {
        if (row == null) {
            return null;
        }
        ResourceMetaDto dto = new ResourceMetaDto();
        dto.setRouteName(row.getRouteName());
        dto.setComponent(row.getComponent());
        dto.setRedirect(row.getRedirect());
        dto.setIcon(row.getIcon());
        dto.setLink(row.getLink());
        dto.setHide(row.getHide());
        dto.setKeepAlive(row.getKeepAlive());
        dto.setAffix(row.getAffix());
        dto.setIframe(row.getIframe());
        return dto;
    }

    public static ResourceMeta toEntity(Long resourceId, ResourceMetaDto dto) {
        ResourceMeta row = new ResourceMeta();
        row.setResourceId(resourceId);
        row.setRouteName(dto.getRouteName());
        row.setComponent(dto.getComponent());
        row.setRedirect(dto.getRedirect());
        row.setIcon(dto.getIcon());
        row.setLink(dto.getLink());
        // 建表是 NOT NULL DEFAULT 0，但 updateById 会把 null 当「不改」，
        // 于是覆盖式保存里取消勾选就改不掉了。这里补成 false。
        row.setHide(Boolean.TRUE.equals(dto.getHide()));
        row.setKeepAlive(Boolean.TRUE.equals(dto.getKeepAlive()));
        row.setAffix(Boolean.TRUE.equals(dto.getAffix()));
        row.setIframe(Boolean.TRUE.equals(dto.getIframe()));
        return row;
    }
}
