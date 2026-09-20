package cn.miniants.platform.integration.outbound;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundLogTest {

    @Test
    void formatUsesStableKeys() {
        String line = OutboundLog.format(new OutboundLog.Event(
                "demo", 200, 86, "POST", "/pay/pre", true, "jourNo:1", null));
        assertEquals(
                "peer=demo dir=out status=200 ms=86 method=POST path=/pay/pre ok=true biz=jourNo:1 err=-",
                line);
    }

    @Test
    void pathOfStripsHostAndQuery() {
        assertEquals(
                "/gmis5/api/grade",
                OutboundLog.pathOf("https://example.test/gmis5/api/grade?userid=xjzc&passwd=secret"));
        assertEquals("-", OutboundLog.pathOf(" "));
    }

    @Test
    void callWithBizPutsAndRestoresMdc() {
        MDC.put(OutboundLog.MDC_BIZ, "previous");
        String seen = OutboundLog.callWithBiz("jourNo:1", OutboundLog::currentBiz);
        assertEquals("jourNo:1", seen);
        assertEquals("previous", MDC.get(OutboundLog.MDC_BIZ));
        MDC.remove(OutboundLog.MDC_BIZ);
    }

    @Test
    void tokenDropsWhitespace() {
        assertTrue(OutboundLog.token("a b=c").indexOf(' ') < 0);
        assertEquals("a_b_c", OutboundLog.token("a b=c"));
    }
}
