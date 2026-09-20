package cn.miniants.platform.ratelimit;

/**
 * 限流主体类型（供 Web 层解析器选用）。
 */
public enum RateLimitSubjectType {

    CLIENT_IP,
    USER_ID,
    CUSTOM
}
