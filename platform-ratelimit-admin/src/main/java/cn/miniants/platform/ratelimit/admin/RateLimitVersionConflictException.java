package cn.miniants.platform.ratelimit.admin;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;

/**
 * 乐观锁冲突，管理端返回 HTTP 409 并附带服务端最新版本。
 */
public class RateLimitVersionConflictException extends PlatformException {

    private final RateLimitPolicyVo current;

    public RateLimitVersionConflictException(RateLimitPolicyVo current) {
        super("策略不存在或已被修改，请刷新后重试");
        this.current = current;
    }

    public RateLimitPolicyVo current() {
        return current;
    }
}
