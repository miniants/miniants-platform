package cn.miniants.platform.integration.idempotency;

import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;

import java.time.Duration;

public interface IdempotencyStore {

    /**
     * 第一次占用返回 true；TTL 内再次占用返回 false。
     */
    boolean tryBegin(String key, Duration ttl);

    default void requireBegin(String key, Duration ttl) {
        if (!tryBegin(key, ttl)) {
            throw new PlatformException(PlatformCodes.DUPLICATE_REQUEST);
        }
    }
}
