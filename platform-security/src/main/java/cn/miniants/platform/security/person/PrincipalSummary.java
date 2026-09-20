package cn.miniants.platform.security.person;

/**
 * 登录响应 principals 条目（不进签名 JWT）。
 */
public record PrincipalSummary(
        Long accountId,
        String username,
        String displayName
) {
}
