package cn.miniants.platform.data;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 启动时记录 MP 全局前缀与 JDBC 前缀；允许 MP 空串 + JDBC 回落 {@code sys_}（实体写死表名时）。
 */
public class PlatformTablePrefixReporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformTablePrefixReporter.class);

    private final PlatformDataProperties properties;
    private final MybatisPlusProperties mybatisPlusProperties;

    public PlatformTablePrefixReporter(
            PlatformDataProperties properties,
            MybatisPlusProperties mybatisPlusProperties) {
        this.properties = properties;
        this.mybatisPlusProperties = mybatisPlusProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mpPrefix = mybatisPlusProperties.getGlobalConfig().getDbConfig().getTablePrefix();
        String jdbcPrefix = properties.jdbcTablePrefix();
        log.info("[pl] 表前缀: mybatis-plus.global-config.db-config.table-prefix='{}', platform.data.jdbcTablePrefix()='{}'",
                mpPrefix == null ? "" : mpPrefix, jdbcPrefix);
    }
}
