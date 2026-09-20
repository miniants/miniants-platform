package cn.miniants.platform.security;

import java.time.LocalDateTime;

public record OperLogEntry(
        String title,
        String eventType,
        String httpMethod,
        String requestUri,
        String requestParam,
        boolean success,
        String errorMessage,
        Long operatorId,
        String operatorName,
        int costMs,
        int httpStatus,
        String requestIp,
        String responseSummary,
        String traceId,
        LocalDateTime occurredAt
) {
    public OperLogEntry(
            String title,
            String eventType,
            String httpMethod,
            String requestUri,
            String requestParam,
            boolean success,
            String errorMessage,
            Long operatorId,
            String operatorName,
            int costMs) {
        this(title, eventType, httpMethod, requestUri, requestParam, success, errorMessage,
                operatorId, operatorName, costMs, 0, null, null, null, null);
    }
}
