package cn.miniants.platform.observability;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.LayoutBase;

/**
 * 同一事件里只要换行，续行都套上第一行前缀（含 traceId）。
 * Grafana / Loki 按行拆事件后仍能检索。
 */
public class PrefixedLineEncoder extends PatternLayoutEncoder {

    @Override
    public void start() {
        super.start();
        Layout<ILoggingEvent> inner = this.layout;
        LayoutBase<ILoggingEvent> wrap = new LayoutBase<>() {
            @Override
            public String doLayout(ILoggingEvent event) {
                return prefixContinuations(inner.doLayout(event));
            }
        };
        wrap.setContext(context);
        wrap.start();
        this.layout = wrap;
    }

    /**
     * 第一行已含 pattern 前缀；其后每一行（正文换行、堆栈）都复制到 {@code | } 为止的前缀。
     */
    static String prefixContinuations(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        boolean crlf = text.endsWith("\r\n");
        boolean lf = !crlf && text.endsWith("\n");
        String body = crlf ? text.substring(0, text.length() - 2)
                : lf ? text.substring(0, text.length() - 1)
                : text;
        int breakAt = indexOfLineBreak(body);
        if (breakAt < 0) {
            return text;
        }
        String firstLine = body.substring(0, breakAt);
        String prefix = linePrefix(firstLine);
        if (prefix.isEmpty()) {
            return text;
        }
        String rest = body.substring(skipLineBreak(body, breakAt));
        String[] lines = rest.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder(text.length() + prefix.length() * lines.length);
        sb.append(firstLine);
        for (String line : lines) {
            sb.append('\n').append(prefix).append(line);
        }
        if (crlf) {
            sb.append("\r\n");
        } else if (lf) {
            sb.append('\n');
        }
        return sb.toString();
    }

    static String linePrefix(String firstLine) {
        int sep = firstLine.indexOf(" | ");
        if (sep < 0) {
            return "";
        }
        return firstLine.substring(0, sep + 3);
    }

    private static int indexOfLineBreak(String text) {
        int n = text.indexOf('\n');
        int r = text.indexOf('\r');
        if (n < 0) {
            return r;
        }
        if (r < 0) {
            return n;
        }
        return Math.min(n, r);
    }

    private static int skipLineBreak(String text, int at) {
        if (text.charAt(at) == '\r' && at + 1 < text.length() && text.charAt(at + 1) == '\n') {
            return at + 2;
        }
        return at + 1;
    }
}
