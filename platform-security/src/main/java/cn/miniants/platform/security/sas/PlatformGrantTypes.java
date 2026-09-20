package cn.miniants.platform.security.sas;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

public final class PlatformGrantTypes {

    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");
    public static final AuthorizationGrantType EXTERNAL = new AuthorizationGrantType("external");
    public static final AuthorizationGrantType QR = new AuthorizationGrantType("qr");

    private PlatformGrantTypes() {
    }

    public static AuthorizationGrantType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PASSWORD;
        }
        return switch (value.trim()) {
            case "external" -> EXTERNAL;
            case "qr" -> QR;
            case "password" -> PASSWORD;
            default -> new AuthorizationGrantType(value.trim());
        };
    }
}
