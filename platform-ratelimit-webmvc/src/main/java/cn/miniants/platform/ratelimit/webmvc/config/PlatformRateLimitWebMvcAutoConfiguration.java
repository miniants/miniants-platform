package cn.miniants.platform.ratelimit.webmvc.config;

import cn.miniants.platform.ratelimit.client.ClientAddressResolver;
import cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration;
import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.webmvc.RateLimitExceededAdvice;
import cn.miniants.platform.ratelimit.webmvc.RateLimitHandlerInterceptor;
import cn.miniants.platform.ratelimit.webmvc.RateLimitMvcSupport;
import cn.miniants.platform.ratelimit.webmvc.RateLimitStartupValidator;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

/**
 * Servlet WebMVC 限流自动配置。
 */
@AutoConfiguration(after = PlatformRateLimitCoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "platform.ratelimit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class PlatformRateLimitWebMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RateLimitPolicyRegistry.class, ClientAddressResolver.class})
    public RateLimitMvcSupport rateLimitMvcSupport(
            RateLimitPolicyRegistry policyRegistry,
            ClientAddressResolver clientAddressResolver,
            BeanFactory beanFactory) {
        return new RateLimitMvcSupport(policyRegistry, clientAddressResolver, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RateLimiter.class, RateLimitMvcSupport.class})
    public RateLimitHandlerInterceptor rateLimitHandlerInterceptor(
            RateLimiter rateLimiter,
            RateLimitMvcSupport support) {
        return new RateLimitHandlerInterceptor(rateLimiter, support);
    }

    @Bean
    @ConditionalOnBean(RateLimitHandlerInterceptor.class)
    public WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitHandlerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .order(Ordered.HIGHEST_PRECEDENCE + 50);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitExceededAdvice rateLimitExceededAdvice() {
        return new RateLimitExceededAdvice();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RequestMappingHandlerMapping.class, RateLimitMvcSupport.class})
    public RateLimitStartupValidator rateLimitStartupValidator(
            List<RequestMappingHandlerMapping> handlerMappings,
            RateLimitMvcSupport support) {
        return new RateLimitStartupValidator(handlerMappings, support);
    }
}
