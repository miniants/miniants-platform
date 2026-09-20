package cn.miniants.platform.tenant.database;

import cn.miniants.platform.tenant.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final String defaultTenant;

    public TenantRoutingDataSource(String defaultTenant) {
        this.defaultTenant = defaultTenant;
        setLenientFallback(false);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        String tenant = TenantContext.current().orElse(defaultTenant);
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalStateException("租户上下文为空且未配置 default-tenant");
        }
        return tenant;
    }
}
