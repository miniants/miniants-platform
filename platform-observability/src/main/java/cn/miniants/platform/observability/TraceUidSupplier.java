package cn.miniants.platform.observability;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 当前请求写入 MDC 的 uid。匿名返回 {@link TraceIds#ANON_UID}。
 *
 * <p>写 access 前缀时必须走 {@link #current(HttpServletRequest)}：
 * {@link #current()} 依赖 {@code RequestContextHolder}，在最外层 Filter 的
 * finally 里已经被 DispatcherServlet 清掉，只能看到匿名。
 */
@FunctionalInterface
public interface TraceUidSupplier {

    String current();

    default String current(HttpServletRequest request) {
        return current();
    }
}
