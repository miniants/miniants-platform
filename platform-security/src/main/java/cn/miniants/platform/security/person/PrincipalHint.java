package cn.miniants.platform.security.person;

/**
 * 花名册等扩账号提示：已知人之后还应具备的 username。
 */
public record PrincipalHint(
        String username,
        String displayName,
        boolean createIfMissing
) {
}
