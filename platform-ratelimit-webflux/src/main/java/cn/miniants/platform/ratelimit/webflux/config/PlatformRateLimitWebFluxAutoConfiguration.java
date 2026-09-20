package cn.miniants.platform.ratelimit.webflux.config;

import cn.miniants.platform.ratelimit.client.ClientAddressResolver;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.spi.ReactiveRateLimiter;
import cn.miniants.platform.ratelimit.webflux.RateLimitRouteIndex;
import cn.miniants.platform.ratelimit.webflux.RateLimitWebExceptionHandler;
import cn.miniants.platform.ratelimit.webflux.RateLimitWebFilter;
import cn.miniants.platform.ratelimit.webflux.RateLimitWebFluxExceededAdvice;
import cn.miniants.platform.ratelimit.webflux.RateLimitWebFluxStartupValidator;
import cn.miniants.platform.ratelimit.webflux.RateLimitWebFluxSupport;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * WebFlux 限流自动配置。
 *
 * <p>优先使用 {@link ReactiveRateLimiter}；若仅有同步 {@link RateLimiter}，则包装为
 * {@code Mono.fromCallable(...).subscribeOn(boundedElastic)}（可能占用弹性线程池，生产更推荐 Redis 响应式实现）。
 */
@AutoConfiguration(after = PlatformRateLimitCoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class PlatformRateLimitWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveRateLimiter.class)
    @ConditionalOnBean(RateLimiter.class)
    @ConditionalOnProperty(prefix = "platform.ratelimit", name = "backend", havingValue = "local", matchIfMissing = true)
    public ReactiveRateLimiter rateLimitReactiveFromSync(RateLimiter rateLimiter) {
        return (request, policy) -> Mono.fromCallable(() -> rateLimiter.acquire(request, policy))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RateLimitPolicyRegistry.class, ClientAddressResolver.class})
    public RateLimitWebFluxSupport rateLimitWebFluxSupport(
            RateLimitPolicyRegistry policyRegistry,
            ClientAddressResolver clientAddressResolver,
            BeanFactory beanFactory) {
        return new RateLimitWebFluxSupport(policyRegistry, clientAddressResolver, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitRouteIndex rateLimitRouteIndex() {
        return new RateLimitRouteIndex();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({ReactiveRateLimiter.class, RateLimitWebFluxSupport.class})
    public RateLimitWebFilter rateLimitWebFilter(
            ReactiveRateLimiter rateLimiter,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex,
            RateLimitProperties properties) {
        return new RateLimitWebFilter(rateLimiter, support, routeIndex, properties.getWebfluxFilterOrder());
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitWebFluxExceededAdvice rateLimitWebFluxExceededAdvice() {
        return new RateLimitWebFluxExceededAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ObjectMapper.class)
    public RateLimitWebExceptionHandler rateLimitWebExceptionHandler(ObjectMapper objectMapper) {
        return new RateLimitWebExceptionHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RequestMappingHandlerMapping.class, RateLimitWebFluxSupport.class, RateLimitRouteIndex.class})
    public RateLimitWebFluxStartupValidator rateLimitWebFluxStartupValidator(
            List<RequestMappingHandlerMapping> handlerMappings,
            RateLimitWebFluxSupport support,
            RateLimitRouteIndex routeIndex,
            RateLimitProperties properties) {
        return new RateLimitWebFluxStartupValidator(
                handlerMappings, support, routeIndex, properties.getWebfluxFilterOrder());
    }
}
