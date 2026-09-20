package cn.miniants.platform.observability;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 访问日志与 MDC 共用的客户端 IP。只读请求头 / remoteAddr，不访问外部系统。
 */
final class ClientIps {

    static final int MAX_LEN = 64;

    private ClientIps() {
    }

    static String resolve(HttpServletRequest request) {
        if (request == null) {
            return TraceIds.ANON_IP;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return sanitize(comma > 0 ? xff.substring(0, comma) : xff);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return sanitize(realIp);
        }
        return sanitize(request.getRemoteAddr());
    }

    static String sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return TraceIds.ANON_IP;
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_LEN));
        for (int i = 0; i < raw.length() && sb.length() < MAX_LEN; i++) {
            char c = raw.charAt(i);
            if (c <= 32) {
                if (sb.length() == 0) {
                    continue;
                }
                break;
            }
            if (c == '|' || c == '[' || c == ']') {
                break;
            }
            sb.append(c);
        }
        return sb.length() == 0 ? TraceIds.ANON_IP : sb.toString();
    }
}
