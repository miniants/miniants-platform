package cn.miniants.platform.data.mapper;

import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 独立成一个自动装配，理由同 {@code PlatformSnowflakeAutoConfiguration}：
 * 为换掉内核 {@code MetaObjectHandler} / 表前缀而整个 exclude
 * {@code PlatformDataAutoConfiguration} 的项目（教务就是），仍然需要 {@code recoverByIds}。
 */
@AutoConfiguration
@ConditionalOnClass(ISqlInjector.class)
public class PlatformSqlInjectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ISqlInjector.class)
    public ISqlInjector platformSqlInjector() {
        return new LogicDeleteSqlInjector();
    }
}
