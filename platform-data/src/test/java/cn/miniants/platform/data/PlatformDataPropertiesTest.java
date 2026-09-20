package cn.miniants.platform.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformDataPropertiesTest {

    @Test
    void defaultTablePrefixIsSys() {
        assertEquals("sys_", new PlatformDataProperties().getTablePrefix());
    }

    @Test
    void blankPrefixStillResolvesSysForJdbc() {
        PlatformDataProperties props = new PlatformDataProperties();
        props.setTablePrefix("");
        assertEquals("sys_", props.jdbcTablePrefix());
        props.setTablePrefix(null);
        assertEquals("sys_", props.jdbcTablePrefix());
        props.setTablePrefix("app_");
        assertEquals("app_", props.jdbcTablePrefix());
    }
}
