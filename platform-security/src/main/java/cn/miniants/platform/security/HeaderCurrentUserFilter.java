package cn.miniants.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 仅 demo / 单测。用请求头注入主体，默认关闭。
 */
public class HeaderCurrentUserFilter extends OncePerRequestFilter {

    public static final String USER = "X-Platform-User";
    public static final String PERMS = "X-Platform-Perms";
    public static final String ADMIN = "X-Platform-Admin";
    public static final String CLIENT = "X-Platform-Client";
    public static final String SCOPES = "X-Platform-Scopes";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userHeader = request.getHeader(USER);
        String clientHeader = request.getHeader(CLIENT);
        if (userHeader != null && !userHeader.isBlank()) {
            String[] parts = userHeader.split(":", 2);
            Long userId = parseLong(parts[0]);
            String username = parts.length > 1 ? parts[1] : parts[0];
            CurrentUser.bind(request, CurrentUser.user(userId, username)
                    .permissions(split(request.getHeader(PERMS)))
                    .sysAdmin("true".equalsIgnoreCase(request.getHeader(ADMIN)))
                    .build());
        } else if (clientHeader != null && !clientHeader.isBlank()) {
            CurrentUser.bind(request, CurrentUser.client(clientHeader)
                    .scopes(split(request.getHeader(SCOPES)))
                    .build());
        }
        filterChain.doFilter(request, response);
    }

    private static Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .forEach(values::add);
        return values;
    }

    private static Long parseLong(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
