package cn.miniants.platform.security;

import cn.miniants.platform.core.error.ErrorCode;
import cn.miniants.platform.core.error.SimpleErrorCode;

public final class SecurityCodes {

    public static final ErrorCode UNAUTHENTICATED =
            new SimpleErrorCode(-1, "platform.security.unauthenticated", "未登录");
    public static final ErrorCode FORBIDDEN =
            new SimpleErrorCode(-1, "platform.security.forbidden", "权限不足");
    public static final ErrorCode UNBOUND =
            new SimpleErrorCode(-1, "platform.security.unbound", "未绑定账号");

    private SecurityCodes() {
    }
}
