package cn.miniants.platform.tenant.database;

import cn.miniants.platform.tenant.TenantProperties;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TenantDataSources {

    private TenantDataSources() {
    }

    public static Map<Object, Object> targets(TenantProperties properties, DataSource fallback) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        properties.getDatabase().getTargets().forEach((id, target) -> {
            if (id == null || id.isBlank() || target == null || target.getUrl() == null || target.getUrl().isBlank()) {
                return;
            }
            targets.put(id.trim(), create(target));
        });
        if (targets.isEmpty() && fallback != null) {
            targets.put(properties.getDefaultTenant(), fallback);
        }
        return targets;
    }

    private static DataSource create(TenantProperties.Target target) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(target.getUrl());
        dataSource.setUsername(target.getUsername());
        dataSource.setPassword(target.getPassword());
        dataSource.setDriverClassName(target.getDriverClassName());
        dataSource.setPoolName("platform-tenant-" + Integer.toHexString(target.getUrl().hashCode()));
        return dataSource;
    }
}
