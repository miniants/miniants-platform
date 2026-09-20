package cn.miniants.platform.security;

import cn.miniants.platform.core.error.PlatformException;

/**
 * 外部身份尚未绑定账号。预期下一步，不是凭证失败。
 */
public final class UnboundAccountException extends PlatformException {

    public UnboundAccountException(String message) {
        super(SecurityCodes.UNBOUND, message);
    }
}
