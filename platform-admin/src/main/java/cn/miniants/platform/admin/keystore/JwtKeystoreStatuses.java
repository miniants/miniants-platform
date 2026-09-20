package cn.miniants.platform.admin.keystore;

/**
 * {@code sys_jwt_keystore.status}：active=当前签发，verify-only=只验签，retired=已摘旧（密文仍保留）。
 */
public final class JwtKeystoreStatuses {

    public static final String ACTIVE = "active";
    public static final String VERIFY_ONLY = "verify-only";
    public static final String RETIRED = "retired";

    private JwtKeystoreStatuses() {
    }

    public static boolean retired(String status) {
        return RETIRED.equals(status);
    }

    public static boolean active(String status) {
        return ACTIVE.equals(status);
    }
}
