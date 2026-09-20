package cn.miniants.platform.integration.outbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * 第三方出站渠道 {@code pl.outbound}。稳定键值一行，不落 body。
 */
public final class OutboundLog {

    public static final String LOGGER_NAME = "pl.outbound";
    public static final String MDC_BIZ = "platform.outbound.biz";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private OutboundLog() {
    }

    public record Event(
            String peer,
            int status,
            long ms,
            String method,
            String path,
            boolean ok,
            String biz,
            String err
    ) {
    }

    public static String format(Event event) {
        return "peer=" + token(event.peer())
                + " dir=out"
                + " status=" + event.status()
                + " ms=" + event.ms()
                + " method=" + token(event.method())
                + " path=" + token(event.path())
                + " ok=" + event.ok()
                + " biz=" + token(event.biz())
                + " err=" + token(event.err());
    }

    public static void emit(Event event) {
        String line = format(event);
        if (event.ok()) {
            LOG.info("{}", line);
        } else {
            LOG.warn("{}", line);
        }
    }

    public static void emit(
            String peer, int status, long ms, String method, String path, boolean ok, String biz, String err) {
        emit(new Event(peer, status, ms, method, path, ok, biz, err));
    }

    public static <T> T callWithBiz(String biz, Supplier<T> action) {
        String previous = MDC.get(MDC_BIZ);
        MDC.put(MDC_BIZ, token(biz));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_BIZ);
            } else {
                MDC.put(MDC_BIZ, previous);
            }
        }
    }

    public static String currentBiz() {
        String biz = MDC.get(MDC_BIZ);
        return biz == null || biz.isBlank() ? "-" : biz;
    }

    public static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "-";
        }
        String value = url.trim();
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            int pathStart = value.indexOf('/', scheme + 3);
            value = pathStart >= 0 ? value.substring(pathStart) : "/";
        }
        return token(value);
    }

    public static String token(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\s=]+", "_");
    }
}
