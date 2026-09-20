package cn.miniants.platform.security;

/**
 * 未绑定账号的提示。客户端和审计可见脱敏线索，不回完整 openid 或证件号。
 */
public final class UnboundAccount {

    public static final String MESSAGE = "未绑定账号";

    private UnboundAccount() {
    }

    public static UnboundAccountException exception(String openId, String username) {
        return new UnboundAccountException(message(openId, username));
    }

    public static boolean matches(String message) {
        return message != null && message.startsWith(MESSAGE);
    }

    public static String message(String openId, String username) {
        String openIdTail = tail(openId, 8);
        String account = accountHint(username);
        if (openIdTail == null && account == null) {
            return MESSAGE;
        }
        StringBuilder sb = new StringBuilder(MESSAGE).append('（');
        if (openIdTail != null) {
            sb.append("openid后8位=").append(openIdTail);
            if (account != null) {
                sb.append('，');
            }
        }
        if (account != null) {
            sb.append("学工号=").append(account);
        }
        return sb.append('）').toString();
    }

    static String tail(String value, int n) {
        if (value == null || value.isBlank() || n <= 0) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= n) {
            return trimmed;
        }
        return trimmed.substring(trimmed.length() - n);
    }

    private static String accountHint(String username) {
        if (username == null || username.isBlank() || "未绑定".equals(username.trim())) {
            return null;
        }
        return username.trim();
    }
}
