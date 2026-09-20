package cn.miniants.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 排在主体绑定之后，把 uid 写进 MDC，供请求中的业务日志使用。
 */
public class TraceUidFilter extends OncePerRequestFilter {

    private final TraceUidSupplier uidSupplier;

    public TraceUidFilter(TraceUidSupplier uidSupplier) {
        this.uidSupplier = uidSupplier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TraceIds.bindUid(uidSupplier.current(request));
        filterChain.doFilter(request, response);
    }
}
