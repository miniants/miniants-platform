package cn.miniants.platform.ratelimit.admin;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * 限流管理表用独立 history。宿主库通常已有业务表，必须把 baseline 定在 0，
 * 否则 Flyway 默认 baselineVersion=1 会跳过 {@code V1__*}。
 */
final class RateLimitAdminFlywaySupport {

    private RateLimitAdminFlywaySupport() {
    }

    static Flyway create(DataSource dataSource, RateLimitAdminProperties properties) {
        RateLimitAdminProperties.Flyway flywayProps = properties.getFlyway();
        return Flyway.configure()
                .dataSource(dataSource)
                .table(flywayProps.getTable())
                .locations(flywayProps.getLocations())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}
