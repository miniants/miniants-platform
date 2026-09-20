package cn.miniants.platform.security.sas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformSasPropertiesTest {

    @Test
    void kidPrefixDefaultsEmptyAndAcceptsCampusPrefix() {
        PlatformSasProperties properties = new PlatformSasProperties();
        assertEquals("", properties.getKidPrefix());
        properties.setKidPrefix("jwt-yjs");
        assertEquals("jwt-yjs", properties.getKidPrefix());
        properties.setKidPrefix(null);
        assertEquals("", properties.getKidPrefix());
    }
}
