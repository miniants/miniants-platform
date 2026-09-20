package cn.miniants.platform.observability.sql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 慢 SQL 告警。只报超阈值的语句；全量 SQL 明细交给 dev 下的 p6spy。
 */
@ConfigurationProperties(prefix = "platform.observability.slow-sql")
public class SlowSqlProperties {

    /** 默认开启：生产同样需要慢 SQL 告警。 */
    private boolean enabled = true;

    /** 超过该毫秒数打 ERROR。 */
    private long thresholdMs = 1000L;

    /** 日志中 SQL 的最大长度，避免超大语句刷爆日志。 */
    private int maxSqlLength = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getThresholdMs() {
        return thresholdMs;
    }

    public void setThresholdMs(long thresholdMs) {
        this.thresholdMs = thresholdMs;
    }

    public int getMaxSqlLength() {
        return maxSqlLength;
    }

    public void setMaxSqlLength(int maxSqlLength) {
        this.maxSqlLength = maxSqlLength;
    }
}
