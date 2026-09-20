package cn.miniants.platform.queue;

import java.util.List;
import java.util.Map;

/**
 * 就绪键路由：BRPOP 听哪些键、delay 推进到哪条、入队投到哪条。
 */
public interface QueueRouting {

    List<String> pollKeys();

    String promoteReadyKey();

    String pushKey(Map<String, String> job);

    static QueueRouting single(String readyKey) {
        if (readyKey == null || readyKey.isBlank()) {
            throw new IllegalArgumentException("ready 键不能为空");
        }
        return new QueueRouting() {
            @Override
            public List<String> pollKeys() {
                return List.of(readyKey);
            }

            @Override
            public String promoteReadyKey() {
                return readyKey;
            }

            @Override
            public String pushKey(Map<String, String> job) {
                return readyKey;
            }
        };
    }
}
