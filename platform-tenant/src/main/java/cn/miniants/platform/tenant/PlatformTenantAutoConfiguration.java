package cn.miniants.platform.tenant;

import cn.miniants.platform.tenant.column.ColumnTenantLineHandler;
import cn.miniants.platform.tenant.database.TenantDataSources;
import cn.miniants.platform.tenant.database.TenantRoutingDataSource;
import cn.miniants.platform.tenant.schema.SchemaRoutingDataSource;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;

@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        beforeName = "cn.miniants.platform.data.PlatformDataAutoConfiguration"
)
@ConditionalOnProperty(prefix = "platform.tenant", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TenantProperties.class)
public class PlatformTenantAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.tenant", name = "header-bind", havingValue = "true")
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public FilterRegistrationBean<HeaderTenantFilter> headerTenantFilter(TenantResolver tenantResolver) {
        FilterRegistrationBean<HeaderTenantFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HeaderTenantFilter(tenantResolver));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 15);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.tenant", name = "strategy", havingValue = "column")
    static class ColumnConfiguration {
        @Bean
        @ConditionalOnMissingBean(name = "tenantLineInnerInterceptor")
        InnerInterceptor tenantLineInnerInterceptor(TenantProperties properties) {
            return new TenantLineInnerInterceptor(new ColumnTenantLineHandler(properties));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.tenant", name = "strategy", havingValue = "schema")
    static class SchemaConfiguration {
        @Bean
        static BeanPostProcessor schemaRoutingDataSourcePostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource
                            && !(bean instanceof SchemaRoutingDataSource)
                            && !(bean instanceof AbstractRoutingDataSource)) {
                        return new SchemaRoutingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "platform.tenant", name = "strategy", havingValue = "database")
    static class DatabaseConfiguration {
        @Bean
        @Primary
        DataSource tenantRoutingDataSource(TenantProperties properties) {
            Map<Object, Object> targets = TenantDataSources.targets(properties, null);
            if (targets.isEmpty()) {
                throw new IllegalStateException("独立库策略需要配置 platform.tenant.database.targets");
            }
            TenantRoutingDataSource routing = new TenantRoutingDataSource(properties.getDefaultTenant());
            routing.setTargetDataSources(targets);
            Object fallback = targets.getOrDefault(properties.getDefaultTenant(), targets.values().iterator().next());
            routing.setDefaultTargetDataSource(fallback);
            routing.afterPropertiesSet();
            return routing;
        }
    }
}
