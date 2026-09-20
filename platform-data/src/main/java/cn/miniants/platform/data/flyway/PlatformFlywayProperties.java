package cn.miniants.platform.data.flyway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务库 Flyway。缺省关。不要占用 {@code spring.flyway}，以免 Boot 默认打到
 * 动态数据源的路由壳，或与内核 {@code platform_schema_history} 抢同一张历史表。
 */
@ConfigurationProperties(prefix = "platform.flyway")
public class PlatformFlywayProperties {

    /** 缺省关。只在 profile 里开。 */
    private boolean enabled;

    /**
     * 动态数据源名。空则用注入的 {@code DataSource}。
     * 有 {@code DynamicRoutingDataSource} 时写 named 名（教务 {@code master}）。
     */
    private String datasource = "";

    private String table = "flyway_schema_history";

    private List<String> locations = new ArrayList<>(List.of("classpath:db/migration"));

    private boolean baselineOnMigrate = true;

    private String baselineVersion = "1";

    private String baselineDescription = "existing schema";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDatasource() {
        return datasource;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public String getBaselineDescription() {
        return baselineDescription;
    }

    public void setBaselineDescription(String baselineDescription) {
        this.baselineDescription = baselineDescription;
    }
}
