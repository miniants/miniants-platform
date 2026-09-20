package cn.miniants.platform.tenant;

public enum TenantStrategyType {
    NONE,
    DATABASE,
    SCHEMA,
    COLUMN;

    public static TenantStrategyType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "database" -> DATABASE;
            case "schema" -> SCHEMA;
            case "column" -> COLUMN;
            default -> NONE;
        };
    }
}
