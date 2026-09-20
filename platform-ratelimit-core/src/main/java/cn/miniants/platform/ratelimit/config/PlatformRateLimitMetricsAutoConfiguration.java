package cn.miniants.platform.ratelimit.config;

import cn.miniants.platform.ratelimit.metrics.MicrometerRateLimitMetrics;
import cn.miniants.platform.ratelimit.metrics.RateLimitMetricsRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformRateLimitCoreAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformRateLimitMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(RateLimitMetricsRecorder.class)
    public RateLimitMetricsRecorder rateLimitMetricsRecorder(MeterRegistry meterRegistry) {
        return new MicrometerRateLimitMetrics(meterRegistry);
    }
}
