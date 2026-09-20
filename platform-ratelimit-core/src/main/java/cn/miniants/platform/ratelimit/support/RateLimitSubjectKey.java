package cn.miniants.platform.ratelimit.support;

/**
 * 限流主体写入 Redis 键与拒绝日志前的消毒：去掉控制字符，截断长度。
 * 不 URL 编码，以便键和日志里仍是可读的 IP / 账号。
 */
public final class RateLimitSubjectKey {

    public static final int MAX_LENGTH = 128;
    public static final String UNKNOWN = "unknown";

    private RateLimitSubjectKey() {
    }

    public static String sanitize(String subject) {
        if (subject == null || subject.isBlank()) {
            return UNKNOWN;
        }
        StringBuilder sb = new StringBuilder(Math.min(subject.length(), MAX_LENGTH));
        for (int i = 0; i < subject.length() && sb.length() < MAX_LENGTH; i++) {
            char ch = subject.charAt(i);
            if (ch < 32 || ch == 127) {
                continue;
            }
            sb.append(ch);
        }
        return sb.isEmpty() ? UNKNOWN : sb.toString();
    }
}
