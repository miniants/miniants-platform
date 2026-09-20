package cn.miniants.platform.admin.support;

/**
 * {@code id=0} 合法，判断是否已持久化不要用 truthy。
 */
public final class PersistIds {

    private PersistIds() {
    }

    public static boolean persisted(Long id) {
        return id != null;
    }
}
