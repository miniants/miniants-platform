package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.json.Jsons;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 出票 JSON：OAuth 标准字段 + additional + {@code OAuth2TokenCustomizer} 写入 JWT 的业务 claim。
 * 协议登记项（iss/sub/exp…）不映到响应体。
 */
final class TokenResponseMaps {

    private static final Set<String> JWT_PROTOCOL_CLAIMS = Set.of(
            "iss", "sub", "aud", "exp", "iat", "nbf", "jti", "ati");

    /** 允许出现在登录 JSON 响应体（非 JWT）的业务 claim。 */
    private static final Set<String> RESPONSE_CLAIM_WHITELIST = Set.of(
            "userId", "username", "name", "sysAdmin", "roleIds",
            "openId", "bizPersona", "real_auth", "principals");

    private TokenResponseMaps() {
    }

    static Map<String, Object> from(OAuth2AccessTokenAuthenticationToken authentication) {
        OAuth2AccessToken access = authentication.getAccessToken();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", access.getTokenValue());
        body.put("token_type", access.getTokenType().getValue());
        if (access.getExpiresAt() != null) {
            body.put("expires_in", Math.max(0, Duration.between(Instant.now(), access.getExpiresAt()).getSeconds()));
        }
        OAuth2RefreshToken refresh = authentication.getRefreshToken();
        if (refresh != null) {
            body.put("refresh_token", refresh.getTokenValue());
        }
        if (access.getScopes() != null && !access.getScopes().isEmpty()) {
            body.put("scope", String.join(" ", access.getScopes()));
        }
        copyCustomizerClaims(body, access.getTokenValue());
        Map<String, Object> additional = authentication.getAdditionalParameters();
        if (additional != null) {
            additional.forEach((key, value) -> {
                if (key != null && !key.isBlank() && !body.containsKey(key) && isAllowedResponseClaim(key)) {
                    body.put(key, value);
                }
            });
        }
        return body;
    }

    static void copyCustomizerClaims(Map<String, Object> body, String accessToken) {
        Map<String, Object> claims = readJwtPayload(accessToken);
        if (claims == null || claims.isEmpty()) {
            return;
        }
        claims.forEach((key, value) -> {
            if (key != null && !key.isBlank()
                    && !JWT_PROTOCOL_CLAIMS.contains(key)
                    && !body.containsKey(key)
                    && isAllowedResponseClaim(key)) {
                body.put(key, value);
            }
        });
    }

    static boolean isAllowedResponseClaim(String key) {
        return RESPONSE_CLAIM_WHITELIST.contains(key);
    }

    private static Map<String, Object> readJwtPayload(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return Map.of();
        }
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) {
            return Map.of();
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = Jsons.readMap(json);
            return payload == null ? Map.of() : payload;
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }
}
