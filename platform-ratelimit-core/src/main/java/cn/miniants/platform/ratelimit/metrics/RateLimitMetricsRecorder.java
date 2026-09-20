package cn.miniants.platform.ratelimit.metrics;

import cn.miniants.platform.ratelimit.RateLimitDecision;

import java.time.Duration;

/**
 * 限流指标。标签只允许 policy / algorithm / backend / outcome。
 */
public interface RateLimitMetricsRecorder {

    RateLimitMetricsRecorder NOOP = new RateLimitMetricsRecorder() {
    };

    default void record(RateLimitDecision decision, Duration latency) {
    }

    default void recordStoreError(String policyCode, String algorithm, String backend) {
    }

    default void recordPublishError(String backend) {
    }
}
