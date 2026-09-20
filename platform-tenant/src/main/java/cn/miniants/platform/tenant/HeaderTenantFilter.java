package cn.miniants.platform.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 仅 demo / 单测。用请求头注入租户，默认关闭。
 */
public class HeaderTenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    public HeaderTenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        tenantResolver.resolve(request).ifPresent(TenantContext::bind);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
