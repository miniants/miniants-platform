package cn.miniants.platform.core.error;

/**
 * 断言失败抛 {@link PlatformException}。
 */
public final class PlatformAssert {

    private PlatformAssert() {
    }

    public static void isTrue(boolean exp, String message) {
        if (!exp) {
            throw new PlatformException(message);
        }
    }

    public static void isTrue(boolean exp, ErrorCode code) {
        if (!exp) {
            throw new PlatformException(code);
        }
    }
}
