package cn.miniants.platform.ratelimit.config;

import cn.miniants.platform.ratelimit.observe.RateLimitEndpoint;
import cn.miniants.platform.ratelimit.observe.RateLimitHealthIndicator;
import cn.miniants.platform.ratelimit.observe.RateLimitRuntimeInspector;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = PlatformRateLimitCoreAutoConfiguration.class)
@ConditionalOnClass(Endpoint.class)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PlatformRateLimitActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitEndpoint rateLimitEndpoint(RateLimitRuntimeInspector inspector) {
        return new RateLimitEndpoint(inspector);
    }

    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean
    public RateLimitHealthIndicator rateLimitHealthIndicator(RateLimitRuntimeInspector inspector) {
        return new RateLimitHealthIndicator(inspector);
    }
}
