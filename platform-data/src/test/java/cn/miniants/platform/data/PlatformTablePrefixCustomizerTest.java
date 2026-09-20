package cn.miniants.platform.data;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformTablePrefixCustomizerTest {

    @Test
    void appliesDefaultWhenMybatisPrefixUnset() {
        PlatformDataProperties props = new PlatformDataProperties();
        MybatisPlusProperties plus = new MybatisPlusProperties();
        new PlatformDataAutoConfiguration().platformTablePrefixCustomizer(props).customize(plus);
        assertEquals("sys_", plus.getGlobalConfig().getDbConfig().getTablePrefix());
    }

    @Test
    void doesNotOverwriteExplicitEmptyMybatisPrefix() {
        PlatformDataProperties props = new PlatformDataProperties();
        MybatisPlusProperties plus = new MybatisPlusProperties();
        plus.getGlobalConfig().getDbConfig().setTablePrefix("");
        new PlatformDataAutoConfiguration().platformTablePrefixCustomizer(props).customize(plus);
        assertEquals("", plus.getGlobalConfig().getDbConfig().getTablePrefix());
    }

    @Test
    void skipsWhenPlatformPrefixBlank() {
        PlatformDataProperties props = new PlatformDataProperties();
        props.setTablePrefix("");
        MybatisPlusProperties plus = new MybatisPlusProperties();
        new PlatformDataAutoConfiguration().platformTablePrefixCustomizer(props).customize(plus);
        assertNull(plus.getGlobalConfig().getDbConfig().getTablePrefix());
    }
}
