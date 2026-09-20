package cn.miniants.platform.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrefixedLineEncoderTest {

    @Test
    void prefixContinuationsCopiesTraceIdOntoEveryLine() {
        String first = "2026-08-27 08:57:48.669 ERROR [161593429da64cf0|-] PlatformWebAdvice    | ";
        String in = first + "internal GET /x\n"
                + "java.lang.IllegalStateException: boom\n"
                + "\tat foo.Bar.baz(Bar.java:1)\n";
        String out = PrefixedLineEncoder.prefixContinuations(in);
        String[] lines = out.stripTrailing().split("\n", -1);
        assertEquals(3, lines.length);
        for (String line : lines) {
            assertTrue(line.startsWith(first), line);
            assertTrue(line.contains("[161593429da64cf0|-]"), line);
        }
        assertTrue(out.endsWith("\n"));
    }

    @Test
    void prefixContinuationsCoversMessageNewlines() {
        String first = "2026-08-27 08:57:48.669 ERROR [a1b2c3d4e5f60708|admin] SomeService          | ";
        String out = PrefixedLineEncoder.prefixContinuations(first + "line1\nline2");
        assertEquals(
                first + "line1\n" + first + "line2",
                out);
    }

    @Test
    void singleLineUnchanged() {
        String line = "2026-08-27 08:57:16.714 INFO  [35ef50f3a98f421e|u] pl.access  | status=200";
        assertEquals(line + "\n", PrefixedLineEncoder.prefixContinuations(line + "\n"));
    }

    @Test
    void encodePrefixesMessageAndStack() {
        LoggerContext context = new LoggerContext();
        PrefixedLineEncoder encoder = new PrefixedLineEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{traceId}|%X{uid:-}|%X{ip:-}] %logger | %msg%n%ex{short}");
        encoder.start();

        LoggingEvent event = new LoggingEvent();
        event.setTimeStamp(System.currentTimeMillis());
        event.setLevel(Level.ERROR);
        event.setLoggerName("cn.miniants.platform.core.advice.PlatformWebAdvice");
        event.setMessage("first\nsecond");
        event.setMDCPropertyMap(Map.of("traceId", "161593429da64cf0", "uid", "-", "ip", "10.0.0.8"));
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));

        String out = new String(encoder.encode(event), StandardCharsets.UTF_8);
        encoder.stop();

        String[] lines = out.stripTrailing().split("\n");
        assertTrue(lines.length >= 3, out);
        for (String line : lines) {
            assertTrue(line.contains("[161593429da64cf0|-|10.0.0.8]"), line);
            assertTrue(line.contains(" | "), line);
        }
    }
}
