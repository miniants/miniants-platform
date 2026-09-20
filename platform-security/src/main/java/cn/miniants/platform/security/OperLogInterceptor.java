package cn.miniants.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

public class OperLogInterceptor implements HandlerInterceptor {

    static final String START_ATTR = OperLogInterceptor.class.getName() + ".start";
    private static final int SUMMARY_MAX = 2048;
    private static final int ERROR_MAX = 512;

    private final OperLogRecorder recorder;
    private final OperLogCustomizer customizer;

    public OperLogInterceptor(OperLogRecorder recorder) {
        this(recorder, null);
    }

    public OperLogInterceptor(OperLogRecorder recorder, OperLogCustomizer customizer) {
        this.recorder = recorder;
        this.customizer = customizer == null ? new OperLogCustomizer() {
        } : customizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            OperLog operLog = handlerMethod.getMethodAnnotation(OperLog.class);
            if (operLog == null) {
                return true;
            }
            customizer.discovered(request, handlerMethod, operLog);
            request.setAttribute(START_ATTR, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        OperLog operLog = handlerMethod.getMethodAnnotation(OperLog.class);
        if (operLog == null) {
            return;
        }
        if (!customizer.shouldRecord(request, handlerMethod, operLog)) {
            return;
        }
        Object start = request.getAttribute(START_ATTR);
        long cost = start instanceof Long startedAt ? System.currentTimeMillis() - startedAt : 0L;
        boolean ok = ex == null && response.getStatus() < 400;
        CurrentUser user = CurrentUser.from(request);
        try {
            OperLogEntry entry = new OperLogEntry(
                    operLog.value(),
                    operLog.eventType(),
                    request.getMethod(),
                    request.getRequestURI(),
                    operLog.captureRequest() ? trim(requestSummary(request), SUMMARY_MAX) : null,
                    ok,
                    ex == null ? null : trim(ex.getMessage(), ERROR_MAX),
                    user == null ? null : user.userId(),
                    operatorName(user),
                    cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost,
                    response.getStatus(),
                    request.getRemoteAddr(),
                    operLog.captureResponse() && ex == null ? "httpStatus=" + response.getStatus() : null,
                    null,
                    LocalDateTime.now());
            recorder.record(customizer.customize(request, response, handlerMethod, operLog, entry));
        } catch (RuntimeException ignored) {
            // 审计失败不能打断业务
        }
    }

    private static String operatorName(CurrentUser user) {
        if (user == null) {
            return null;
        }
        if (user.name() != null && !user.name().isBlank()) {
            return user.name();
        }
        if (user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return user.clientId();
    }

    private static String requestSummary(HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getMethod()).append(' ').append(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
