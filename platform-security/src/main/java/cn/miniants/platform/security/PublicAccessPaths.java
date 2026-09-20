package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;

/**
 * 协议口与 {@code @PublicAccess} 扫描结果。{@code PermissionInterceptor} 用来盖 {@code public-ok:*}。
 *
 * <p>两份来源：{@link PlatformSecurityProperties#resolvedAnonymousPaths()} 的协议口，
 * 以及启动扫描写入的 {@link PublicAccess} mapping。业务 URL 不要手写进协议口。
 */
public final class PublicAccessPaths {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private volatile List<String> protocolPatterns = List.of();
    private volatile List<String> mappingPatterns = List.of();

    public void replaceProtocol(Collection<String> next) {
        this.protocolPatterns = copy(next);
    }

    public void replace(Collection<String> next) {
        this.mappingPatterns = copy(next);
    }

    public List<String> protocolPatterns() {
        return protocolPatterns;
    }

    public List<String> patterns() {
        return mappingPatterns;
    }

    public boolean matches(HttpServletRequest request, String path) {
        return matches(path);
    }

    public boolean matches(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path != null && contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return matches(path);
    }

    public boolean matches(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return match(protocolPatterns, path) || match(mappingPatterns, path);
    }

    public static PublicAccessPaths of(Collection<String> patterns) {
        PublicAccessPaths paths = new PublicAccessPaths();
        paths.replace(patterns);
        return paths;
    }

    private boolean match(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private static List<String> copy(Collection<String> next) {
        return next == null ? List.of() : List.copyOf(next);
    }
}
