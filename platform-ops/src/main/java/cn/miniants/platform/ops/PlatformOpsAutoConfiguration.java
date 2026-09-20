package cn.miniants.platform.ops;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.ops", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpsProperties.class)
public class PlatformOpsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RuntimeDrain runtimeDrain() {
        return new LocalRuntimeDrain();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessRuntimeContributor processRuntimeContributor(RuntimeDrain drain) {
        return new ProcessRuntimeContributor(drain);
    }

    @Bean
    @ConditionalOnMissingBean
    public RuntimeDrainLifecycle runtimeDrainLifecycle(RuntimeDrain drain) {
        return new RuntimeDrainLifecycle(drain);
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalRuntimeController internalRuntimeController(
            ObjectProvider<RuntimeStateContributor> contributors) {
        return new InternalRuntimeController(contributors);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    static class WebConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "internalRuntimeAccessFilter")
        FilterRegistrationBean<InternalRuntimeAccessFilter> internalRuntimeAccessFilter() {
            FilterRegistrationBean<InternalRuntimeAccessFilter> bean = new FilterRegistrationBean<>();
            bean.setFilter(new InternalRuntimeAccessFilter());
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            bean.addUrlPatterns(InternalRuntimeAccessFilter.INTERNAL_RUNTIME_PATH,
                    InternalRuntimeAccessFilter.INTERNAL_RUNTIME_PATH + "/*");
            return bean;
        }
    }
}
