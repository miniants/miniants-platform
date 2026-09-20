package cn.miniants.platform.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.tenant")
public class TenantProperties {

    private boolean enabled = false;
    private String strategy = "none";
    private String defaultTenant = "default";
    private String column = "tenant_id";
    private boolean headerBind = false;
    private List<String> ignoreTables = new ArrayList<>(List.of(
            "sys_tenant", "sys_user", "sys_role", "sys_user_role", "sys_oauth_client",
            "sys_resource", "sys_role_resource", "sys_dict", "sys_config", "sys_oper_log",
            "platform_schema_history", "flyway_schema_history"));
    private Database database = new Database();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TenantStrategyType strategyType() {
        if (!enabled) {
            return TenantStrategyType.NONE;
        }
        return TenantStrategyType.from(strategy);
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getDefaultTenant() {
        return defaultTenant;
    }

    public void setDefaultTenant(String defaultTenant) {
        this.defaultTenant = defaultTenant;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public boolean isHeaderBind() {
        return headerBind;
    }

    public void setHeaderBind(boolean headerBind) {
        this.headerBind = headerBind;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        this.ignoreTables = ignoreTables == null ? new ArrayList<>() : ignoreTables;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database == null ? new Database() : database;
    }

    public static class Database {
        private Map<String, Target> targets = new LinkedHashMap<>();

        public Map<String, Target> getTargets() {
            return targets;
        }

        public void setTargets(Map<String, Target> targets) {
            this.targets = targets == null ? new LinkedHashMap<>() : targets;
        }
    }

    public static class Target {
        private String url;
        private String username = "sa";
        private String password = "";
        private String driverClassName = "org.h2.Driver";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
    }
}
