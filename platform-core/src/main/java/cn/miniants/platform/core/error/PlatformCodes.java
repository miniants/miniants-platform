package cn.miniants.platform.core.error;

/**
 * 内核错误码。
 *
 * <p>{@code code()} 只有 200 / -1 / 301 三个取值，是四端已固化的线上契约，不要扩充；
 * 区分语义靠 {@code key()}，它同时是 i18n 的查找键。新增错误码时同步补
 * {@code messages/platform-errors*.properties}。
 */
public final class PlatformCodes {

    public static final ErrorCode SUCCESS = new SimpleErrorCode(200, "platform.core.success", "执行成功");
    public static final ErrorCode FAILED = new SimpleErrorCode(-1, "platform.core.failed", "操作失败");
    public static final ErrorCode WAIT = new SimpleErrorCode(301, "platform.core.wait", "等待中");
    public static final ErrorCode INTERNAL = new SimpleErrorCode(-1, "platform.core.internal", "服务暂不可用，请稍后重试");
    public static final ErrorCode BAD_REQUEST = new SimpleErrorCode(-1, "platform.core.bad-request", "请求参数不正确");
    public static final ErrorCode LOCK_NOT_ACQUIRED =
            new SimpleErrorCode(-1, "platform.core.lock-not-acquired", "获取锁失败");
    public static final ErrorCode DUPLICATE_REQUEST =
            new SimpleErrorCode(-1, "platform.core.duplicate-request", "重复请求");
    public static final ErrorCode TOO_MANY_REQUESTS =
            new SimpleErrorCode(-1, "platform.core.too-many-requests", "请求过于频繁");

    private PlatformCodes() {
    }
}
