package cn.miniants.platform.integration.lock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 记录本线程在某个锁实例上持有的令牌。
 *
 * <p>租约到期后锁会被别人抢走，此时若还按 key 直接删除就会误删他人的锁，所以释放必须凭令牌。
 *
 * <p>每个锁实例各持一份：不同实例可能带不同键前缀，共用一张表会让同名 key 互相顶掉。
 */
final class LockTokens {

    private final ThreadLocal<Map<String, String>> held = ThreadLocal.withInitial(HashMap::new);

    static String newToken() {
        return UUID.randomUUID().toString();
    }

    /** 抢到之后才登记，抢失败不留痕，否则后续 unlock 会发出无意义的释放。 */
    void hold(String key, String token) {
        held.get().put(key, token);
    }

    /** 返回本线程为该 key 持有的令牌并清除；未持有返回 null。 */
    String release(String key) {
        Map<String, String> tokens = held.get();
        String token = tokens.remove(key);
        if (tokens.isEmpty()) {
            held.remove();
        }
        return token;
    }
}
