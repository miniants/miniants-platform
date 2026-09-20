package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.core.error.PlatformException;

public final class JwtWrapSources {

    public static final String ENV = "env";
    public static final String DATABASE = "database";

    private JwtWrapSources() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return ENV;
        }
        String value = raw.trim().toLowerCase();
        if (ENV.equals(value) || DATABASE.equals(value)) {
            return value;
        }
        throw new PlatformException("包装密钥来源只能是 env 或 database");
    }
}
