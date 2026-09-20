package cn.miniants.platform.integration.secret;

import cn.miniants.platform.core.error.PlatformException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentSecretProviderTest {

    @Test
    void findsConfiguredValueAndDoesNotEchoOnRequire() {
        SecretProperties properties = new SecretProperties();
        properties.getValues().put("demo-key", "s3cret");
        EnvironmentSecretProvider provider = new EnvironmentSecretProvider(properties);

        assertEquals("s3cret", provider.require("demo-key"));
        assertTrue(provider.find("missing").isEmpty());

        PlatformException ex = assertThrows(PlatformException.class, () -> provider.require("missing"));
        assertEquals("未配置密钥", ex.getMessage());
        assertTrue(ex.getMessage().indexOf("s3cret") < 0);
    }
}
