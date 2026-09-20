package cn.miniants.platform.admin.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageQueriesTest {

    @Test
    void optionalLongTreatsBlankAsAbsent() {
        assertNull(PageQueries.optionalLong(null));
        assertNull(PageQueries.optionalLong(""));
        assertNull(PageQueries.optionalLong("  "));
    }

    @Test
    void optionalLongKeepsZero() {
        assertEquals(0L, PageQueries.optionalLong("0"));
        assertEquals(10L, PageQueries.optionalLong("10"));
    }

    @Test
    void optionalLongRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> PageQueries.optionalLong("x"));
    }
}
