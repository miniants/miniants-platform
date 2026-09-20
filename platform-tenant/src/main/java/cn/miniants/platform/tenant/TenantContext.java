package cn.miniants.platform.tenant;

import cn.miniants.platform.core.error.PlatformException;

import java.util.Optional;

/**
 * 当前租户。不要用 truthy 判断是否已绑定。
 */
public final class TenantContext {

    private static final ThreadLocal<String> HOLDER = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void bind(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            clear();
            return;
        }
        HOLDER.set(tenantId.trim());
    }

    public static Optional<String> current() {
        String value = HOLDER.get();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public static String require() {
        return current().orElseThrow(() -> new PlatformException("未指定租户"));
    }

    public static void clear() {
        HOLDER.remove();
    }
}
