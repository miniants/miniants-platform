package cn.miniants.platform.security.sas.keystore;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * kid 拼接规则。本层不生成密钥；admin 生成新钥时调用。
 *
 * <p>{@code <prefix>-<yyyyMMddHHmmss>}。prefix 空白时退回 alias（再拼时间戳，避免与
 * classpath 兜底 {@code kid=alias} 撞车）；alias 也空白则随机 UUID。
 */
public final class JwtKids {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private JwtKids() {
    }

    /**
     * 按 {@code platform.security.sas.kid-prefix} 与 keystore.alias 生成下一把 kid。
     */
    public static String next(String prefix, String fallbackAlias) {
        String ts = LocalDateTime.now().format(TIMESTAMP);
        if (prefix != null && !prefix.isBlank()) {
            return prefix.trim() + "-" + ts;
        }
        if (fallbackAlias != null && !fallbackAlias.isBlank()) {
            return fallbackAlias.trim() + "-" + ts;
        }
        return UUID.randomUUID().toString();
    }
}
