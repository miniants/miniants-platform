package cn.miniants.platform.queue;

/**
 * Redis 关闭 / 瞬断分类与异常摘要。
 */
final class RedisDisconnectClassifier {

    private RedisDisconnectClassifier() {
    }

    public static boolean isRedisFactoryStopping(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = String.valueOf(t.getMessage());
            if (msg.contains("STOPPING") || msg.contains("STOPPED")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 空队列 BRPOP 被 Lettuce 命令超时掐掉：不是故障，工人应再阻塞。
     */
    public static boolean isIdlePollTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if ("QueryTimeoutException".equals(name) || "RedisCommandTimeoutException".equals(name)) {
                return true;
            }
            String msg = String.valueOf(t.getMessage());
            if (msg.contains("Command timed out")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTransientRedisDisconnect(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InterruptedException) {
                return true;
            }
            String msg = String.valueOf(t.getMessage());
            if (msg.contains("Connection closed")
                    || msg.contains("connection timed out")
                    || msg.contains("Unable to connect")) {
                return true;
            }
            String name = t.getClass().getSimpleName();
            if (name.contains("RedisConnection") || name.contains("RedisSystem")) {
                if (msg.contains("Connection") || msg.contains("connect") || msg.contains("closed")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String summarize(Throwable ex) {
        if (ex == null) {
            return "";
        }
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return root.getClass().getSimpleName()
                + (msg == null || msg.isBlank() ? "" : ": " + msg);
    }
}
