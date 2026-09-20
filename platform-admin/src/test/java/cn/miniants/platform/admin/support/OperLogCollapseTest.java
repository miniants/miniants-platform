package cn.miniants.platform.admin.support;

import cn.miniants.platform.admin.entity.OperLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperLogCollapseTest {

    @Test
    void emptyStaysEmpty() {
        assertTrue(OperLogCollapse.mergeConsecutive(List.of()).isEmpty());
    }

    @Test
    void consecutiveSameGroupCollapses() {
        OperLog first = row(1L, "alice", "1.1.1.1", "/a", "e");
        OperLog second = row(2L, "alice", "1.1.1.1", "/a", "e");
        List<OperLog> collapsed = OperLogCollapse.mergeConsecutive(List.of(first, second));
        assertEquals(1, collapsed.size());
        assertEquals(1L, collapsed.get(0).getId());
        assertEquals(2, collapsed.get(0).getRepeatCount());
    }

    @Test
    void laterSameGroupDoesNotJoinEarlierBurst() {
        OperLog a1 = row(1L, "alice", "1.1.1.1", "/a", null);
        OperLog b = row(2L, "bob", "1.1.1.1", "/a", null);
        OperLog a2 = row(3L, "alice", "1.1.1.1", "/a", "");
        List<OperLog> collapsed = OperLogCollapse.mergeConsecutive(List.of(a1, b, a2));
        assertEquals(3, collapsed.size());
        assertEquals(1, collapsed.get(0).getRepeatCount());
        assertEquals(1, collapsed.get(1).getRepeatCount());
        assertEquals(1, collapsed.get(2).getRepeatCount());
        assertTrue(OperLogCollapse.sameGroup(a1, a2));
    }

    private static OperLog row(Long id, String name, String ip, String uri, String error) {
        OperLog row = new OperLog();
        row.setId(id);
        row.setOperatorName(name);
        row.setRequestIp(ip);
        row.setRequestUri(uri);
        row.setErrorMessage(error);
        return row;
    }
}
