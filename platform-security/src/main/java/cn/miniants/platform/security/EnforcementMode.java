package cn.miniants.platform.security;

public enum EnforcementMode {
    OFF,
    SHADOW,
    ENFORCE;

    public static EnforcementMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SHADOW;
        }
        return switch (raw.trim().toLowerCase()) {
            case "off" -> OFF;
            case "enforce" -> ENFORCE;
            default -> SHADOW;
        };
    }

    public String wire() {
        return name().toLowerCase();
    }
}
