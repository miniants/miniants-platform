package cn.miniants.platform.queue;

/**
 * 失败后延迟秒数：第 1/2/3 次 30/120/300，之后保持 300。
 */
final class RetryBackoff {

    private static final long[] DELAY_SECONDS = {30L, 120L, 300L};

    private RetryBackoff() {
    }

    /**
     * @param finishedAttempt 刚结束的尝试序号（从 1 起）
     */
    public static long secondsAfterAttempt(int finishedAttempt) {
        int idx = finishedAttempt - 1;
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= DELAY_SECONDS.length) {
            return DELAY_SECONDS[DELAY_SECONDS.length - 1];
        }
        return DELAY_SECONDS[idx];
    }
}
