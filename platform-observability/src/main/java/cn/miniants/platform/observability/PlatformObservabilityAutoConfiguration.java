package cn.miniants.platform.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;

@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class PlatformObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceIdClientHttpRequestInterceptor traceIdClientHttpRequestInterceptor() {
        return new TraceIdClientHttpRequestInterceptor();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class WebConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "traceIdFilter")
        FilterRegistrationBean<TraceIdFilter> traceIdFilter(
                ObservabilityProperties properties, TraceUidSupplier uidSupplier) {
            FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new TraceIdFilter(properties.isAccessLog(), uidSupplier));
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
            bean.addUrlPatterns("/*");
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "cn.miniants.platform.security.CurrentUser")
    static class SecurityUidConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceUidSupplier.class)
        TraceUidSupplier currentUserTraceUidSupplier() {
            return new CurrentUserTraceUidSupplier();
        }

        @Bean
        @ConditionalOnClass(name = "jakarta.servlet.Filter")
        FilterRegistrationBean<TraceUidFilter> traceUidFilter(TraceUidSupplier uidSupplier) {
            FilterRegistrationBean<TraceUidFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new TraceUidFilter(uidSupplier));
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 25);
            bean.addUrlPatterns("/*");
            return bean;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("cn.miniants.platform.security.CurrentUser")
    static class AnonymousUidConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceUidSupplier.class)
        TraceUidSupplier anonymousTraceUidSupplier() {
            return () -> TraceIds.ANON_UID;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.client.RestTemplate")
    static class RestTemplateConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "traceIdRestTemplateCustomizer")
        RestTemplateCustomizer traceIdRestTemplateCustomizer(TraceIdClientHttpRequestInterceptor interceptor) {
            return restTemplate -> restTemplate.getInterceptors().add(interceptor);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.client.RestClient")
    static class RestClientConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "traceIdRestClientCustomizer")
        RestClientCustomizer traceIdRestClientCustomizer(TraceIdClientHttpRequestInterceptor interceptor) {
            return builder -> builder.requestInterceptor(interceptor);
        }
    }

}
