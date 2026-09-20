package cn.miniants.platform.admin.dto;

/**
 * 包装密钥来源。不含密钥材料。
 */
public class JwtWrapSettingsVo {

    private String source;
    private boolean envConfigured;
    private boolean databaseReady;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isEnvConfigured() {
        return envConfigured;
    }

    public void setEnvConfigured(boolean envConfigured) {
        this.envConfigured = envConfigured;
    }

    public boolean isDatabaseReady() {
        return databaseReady;
    }

    public void setDatabaseReady(boolean databaseReady) {
        this.databaseReady = databaseReady;
    }
}
