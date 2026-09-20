package cn.miniants.platform.security;

import java.util.List;
import java.util.Set;

public final class OperLogEventTypes {

    public static final String LOGIN = "login";
    public static final String PASSWORD_CHANGE = "password_change";
    public static final String AUTH_DENY = "auth_deny";
    public static final String AUTH_UNCLASSIFIED = "auth_unclassified";
    public static final String RBAC_CHANGE = "rbac_change";
    public static final String OAUTH_CLIENT = "oauth_client";
    public static final String DATA_MUTATE = "data_mutate";
    public static final String DATA_EXPORT = "data_export";
    public static final String SENSITIVE_READ = "sensitive_read";
    public static final String CONFIG_CHANGE = "config_change";

    public static final List<String> ALL_ORDERED = List.of(
            LOGIN,
            PASSWORD_CHANGE,
            AUTH_DENY,
            AUTH_UNCLASSIFIED,
            RBAC_CHANGE,
            OAUTH_CLIENT,
            DATA_MUTATE,
            DATA_EXPORT,
            SENSITIVE_READ,
            CONFIG_CHANGE);

    public static final Set<String> ALL = Set.copyOf(ALL_ORDERED);

    private OperLogEventTypes() {
    }

    public static boolean isKnown(String eventType) {
        return eventType != null && !eventType.isBlank() && ALL.contains(eventType);
    }

    /** 兼容旧 Redis 分组 id → 新 event_type。 */
    public static String migrateLegacyGroup(String group) {
        if (group == null || group.isBlank()) {
            return null;
        }
        return switch (group) {
            case "write" -> DATA_MUTATE;
            case "unclassified" -> AUTH_UNCLASSIFIED;
            case "deny" -> AUTH_DENY;
            case "ok", "permit" -> null;
            case "login" -> LOGIN;
            default -> isKnown(group) ? group : null;
        };
    }
}
