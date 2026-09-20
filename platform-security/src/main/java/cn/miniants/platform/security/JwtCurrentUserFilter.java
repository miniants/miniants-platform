package cn.miniants.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * 用 Bearer JWT 填 {@link CurrentUser}。已绑定的主体（如单测头）不覆盖。坏票当未登录。
 */
public class JwtCurrentUserFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;

    public JwtCurrentUserFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (CurrentUser.from(request) == null) {
            String token = bearerToken(request);
            if (token != null) {
                try {
                    CurrentUser user = fromJwt(jwtDecoder.decode(token));
                    if (user != null) {
                        CurrentUser.bind(request, user);
                    }
                } catch (JwtException ignored) {
                    AccessAuth.stampJwtInvalid(request);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    public static CurrentUser fromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        Long userId = toLong(jwt.getClaim("userId"));
        String username = textClaim(jwt, "username");
        if (userId != null || hasText(username)) {
            return CurrentUser.user(userId, username)
                    .name(firstText(jwt, "name", "realName"))
                    .clientId(textClaim(jwt, "client_id"))
                    .roleIds(roleIds(jwt))
                    .sysAdmin(booleanClaim(jwt.getClaim("sysAdmin")))
                    .scopes(scopes(jwt))
                    .build();
        }
        String clientId = textClaim(jwt, "client_id");
        if (!hasText(clientId)) {
            clientId = jwt.getSubject();
        }
        if (!hasText(clientId)) {
            return null;
        }
        return CurrentUser.client(clientId).scopes(scopes(jwt)).build();
    }

    private static List<Long> roleIds(Jwt jwt) {
        Object raw = jwt.getClaim("roleIds");
        List<Long> ids = new ArrayList<>();
        if (raw instanceof Collection<?> items) {
            for (Object item : items) {
                Long id = toLong(item);
                if (id != null) {
                    ids.add(id);
                }
            }
        } else {
            Long id = toLong(raw);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Set<String> scopes(Jwt jwt) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        addScopes(values, jwt.getClaim("scope"));
        addScopes(values, jwt.getClaim("scp"));
        return values;
    }

    private static void addScopes(Set<String> values, Object raw) {
        if (raw instanceof Collection<?> items) {
            for (Object item : items) {
                if (item != null) {
                    addScopeTokens(values, item.toString());
                }
            }
        } else if (raw instanceof String text) {
            addScopeTokens(values, text);
        }
    }

    private static void addScopeTokens(Set<String> values, String text) {
        for (String part : text.split("[\\s,]+")) {
            if (!part.isBlank()) {
                values.add(part.trim());
            }
        }
    }

    private static Long toLong(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean booleanClaim(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text.trim());
        }
        return false;
    }


    private static String firstText(Jwt jwt, String primary, String fallback) {
        String value = textClaim(jwt, primary);
        return value != null ? value : textClaim(jwt, fallback);
    }

    private static String textClaim(Jwt jwt, String name) {
        String value = jwt.getClaimAsString(name);
        return hasText(value) ? value : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
