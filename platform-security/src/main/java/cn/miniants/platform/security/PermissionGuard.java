package cn.miniants.platform.security;

import java.util.Collection;

public final class PermissionGuard {

    public enum Outcome {
        ALLOW,
        SHADOW_DENY,
        DENY
    }

    public Outcome decide(String code, boolean admin, Collection<String> granted, EnforcementMode mode) {
        return decide(false, code, admin, granted, mode);
    }

    /**
     * @param ignore 为 true 时直接放行（不校验权限码）
     */
    public Outcome decide(boolean ignore, String code, boolean admin, Collection<String> granted, EnforcementMode mode) {
        if (ignore || admin) {
            return Outcome.ALLOW;
        }
        if (code == null || code.isBlank()) {
            return Outcome.ALLOW;
        }
        if (firstMatch(granted, code) != null) {
            return Outcome.ALLOW;
        }
        if (mode == EnforcementMode.ENFORCE) {
            return Outcome.DENY;
        }
        if (mode == EnforcementMode.SHADOW) {
            return Outcome.SHADOW_DENY;
        }
        return Outcome.ALLOW;
    }

    /**
     * {@code required} 可用 {@code |} 表示有其一即可。用户码不把 {@code all} 当万能。
     * 命中时返回第一个匹配码；未命中返回 {@code null}。
     */
    public static String firstMatch(Collection<String> granted, String required) {
        if (required == null || required.isBlank() || granted == null || granted.isEmpty()) {
            return null;
        }
        for (String part : required.split("\\|")) {
            String code = part.trim();
            if (!code.isEmpty() && granted.contains(code)) {
                return code;
            }
        }
        return null;
    }

    public static boolean matches(Collection<String> granted, String required) {
        if (required == null || required.isBlank()) {
            return true;
        }
        return firstMatch(granted, required) != null;
    }
}
