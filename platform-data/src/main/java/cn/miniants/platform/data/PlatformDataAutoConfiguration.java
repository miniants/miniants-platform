package cn.miniants.platform.data;

import cn.miniants.platform.data.audit.AuditorSupplier;
import cn.miniants.platform.data.audit.EmptyAuditorSupplier;
import cn.miniants.platform.data.audit.PlatformMetaObjectHandler;
import cn.miniants.platform.data.scope.DataScopeAssemblyChecker;
import cn.miniants.platform.data.scope.DataScopeInnerInterceptor;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(PlatformDataProperties.class)
@EnableTransactionManagement
public class PlatformDataAutoConfiguration {

    @Bean
    @ConditionalOnBean({DataScopeInnerInterceptor.class, MybatisPlusInterceptor.class})
    public DataScopeAssemblyChecker dataScopeAssemblyChecker(
            DataScopeInnerInterceptor dataScopeInnerInterceptor,
            MybatisPlusInterceptor mybatisPlusInterceptor) {
        return new DataScopeAssemblyChecker(dataScopeInnerInterceptor, mybatisPlusInterceptor);
    }

    @Bean
    @ConditionalOnBean(MybatisPlusProperties.class)
    public PlatformTablePrefixReporter platformTablePrefixReporter(
            PlatformDataProperties properties,
            MybatisPlusProperties mybatisPlusProperties) {
        return new PlatformTablePrefixReporter(properties, mybatisPlusProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditorSupplier auditorSupplier() {
        return new EmptyAuditorSupplier();
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public PlatformMetaObjectHandler platformMetaObjectHandler(AuditorSupplier auditorSupplier) {
        return new PlatformMetaObjectHandler(auditorSupplier);
    }

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusPropertiesCustomizer platformTablePrefixCustomizer(PlatformDataProperties properties) {
        return plus -> {
            var dbConfig = plus.getGlobalConfig().getDbConfig();
            // dest 会把 MP 前缀显式钉成空串；不要再用默认 sys_ 盖回去。
            if (dbConfig.getTablePrefix() != null) {
                return;
            }
            String prefix = properties.getTablePrefix();
            if (prefix == null || prefix.isBlank()) {
                return;
            }
            dbConfig.setTablePrefix(prefix);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeInnerInterceptor dataScopeInnerInterceptor() {
        return new DataScopeInnerInterceptor();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            @Autowired(required = false) List<InnerInterceptor> extra) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        if (extra != null) {
            extra.forEach(interceptor::addInnerInterceptor);
        }
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
