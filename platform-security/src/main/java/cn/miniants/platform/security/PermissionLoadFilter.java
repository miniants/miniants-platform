package cn.miniants.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 主体已绑定且权限码为空时，用 {@link PermissionLoader} 补上。已有码（如单测请求头）不覆盖。
 */
public class PermissionLoadFilter extends OncePerRequestFilter {

    private final PermissionLoader permissionLoader;

    public PermissionLoadFilter(PermissionLoader permissionLoader) {
        this.permissionLoader = permissionLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CurrentUser user = CurrentUser.from(request);
        if (user != null && user.actor() == Actor.USER && user.permissions().isEmpty()) {
            Set<String> permissions = permissionLoader.load(user.userId(), user.roleIds());
            if (permissions != null && !permissions.isEmpty()) {
                CurrentUser.bind(request, user.toBuilder().permissions(permissions).build());
            }
        }
        filterChain.doFilter(request, response);
    }
}
