package cn.miniants.platform.observability.sql;

import jakarta.annotation.PostConstruct;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;

/**
 * 向每个 SqlSessionFactory 挂慢 SQL 拦截器。多数据源下每个工厂都要挂。
 */
@AutoConfiguration
@ConditionalOnClass(SqlSessionFactory.class)
@EnableConfigurationProperties(SlowSqlProperties.class)
@ConditionalOnProperty(prefix = "platform.observability.slow-sql", name = "enabled", matchIfMissing = true)
public class PlatformSlowSqlAutoConfiguration {

    private final SlowSqlProperties properties;
    private final ObjectProvider<List<SqlSessionFactory>> sqlSessionFactories;

    public PlatformSlowSqlAutoConfiguration(SlowSqlProperties properties,
            ObjectProvider<List<SqlSessionFactory>> sqlSessionFactories) {
        this.properties = properties;
        this.sqlSessionFactories = sqlSessionFactories;
    }

    @PostConstruct
    public void register() {
        List<SqlSessionFactory> factories = sqlSessionFactories.getIfAvailable();
        if (factories == null || factories.isEmpty()) {
            return;
        }
        SlowSqlInterceptor interceptor = new SlowSqlInterceptor(properties);
        for (SqlSessionFactory factory : factories) {
            org.apache.ibatis.session.Configuration configuration = factory.getConfiguration();
            boolean alreadyRegistered = configuration.getInterceptors().stream()
                    .anyMatch(item -> item instanceof SlowSqlInterceptor);
            if (!alreadyRegistered) {
                configuration.addInterceptor(interceptor);
            }
        }
    }
}
