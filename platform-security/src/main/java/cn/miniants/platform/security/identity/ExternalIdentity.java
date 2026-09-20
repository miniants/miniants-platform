package cn.miniants.platform.security.identity;

/**
 * 外部身份绑定到自然人（不绑到某一个 sys_user）。
 */
public record ExternalIdentity(
        String provider,
        String subject,
        Long personId
) {
}
