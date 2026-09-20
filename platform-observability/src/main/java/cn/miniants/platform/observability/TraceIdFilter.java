package cn.miniants.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求入口：生成或透传 traceId，写入 uid/ip，回写响应头，结束时打访问日志并清空 MDC。
 *
 * <p>宿主如需自定义访问日志，应在本过滤器内层的过滤器或请求处理链中记录；
 * 请求离开本过滤器后 traceId、uid 和 ip 不再保留在线程 MDC 中。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private final boolean accessLog;
    private final TraceUidSupplier uidSupplier;

    public TraceIdFilter(boolean accessLog, TraceUidSupplier uidSupplier) {
        this.accessLog = accessLog;
        this.uidSupplier = uidSupplier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = TraceIds.resolve(firstHeader(request, TraceIds.HEADER_TRACE_ID, TraceIds.HEADER_REQUEST_ID));
        TraceIds.bind(traceId);
        TraceIds.bindUid(uidSupplier.current(request));
        TraceIds.bindIp(ClientIps.resolve(request));
        response.setHeader(TraceIds.HEADER_TRACE_ID, traceId);

        long startNs = System.nanoTime();
        Exception error = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            error = ex;
            throw ex;
        } finally {
            try {
                TraceIds.bindUid(uidSupplier.current(request));
                if (accessLog && !AccessLog.shouldSkip(request)) {
                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                    int status = response.getStatus();
                    if (error != null && status < 400) {
                        status = 500;
                    }
                    AccessLog.emit(request, status, elapsedMs, error);
                }
            } finally {
                TraceIds.clear();
            }
        }
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
