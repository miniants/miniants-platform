package cn.miniants.platform.security.qr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisQrLoginStoreDecodeTest {

    @Test
    void decodesJsonAndLegacyLiterals() {
        RedisQrLoginStore.Stored json = RedisQrLoginStore.decode(
                "{\"status\":\"INIT\",\"clientId\":\"APP_WEB\",\"openId\":null}");
        assertThat(json.status).isEqualTo("INIT");
        assertThat(json.clientId).isEqualTo("APP_WEB");

        assertThat(RedisQrLoginStore.decode("INIT").status).isEqualTo("INIT");
        assertThat(RedisQrLoginStore.decode("SCANNED").status).isEqualTo("SCANNED");

        RedisQrLoginStore.Stored success = RedisQrLoginStore.decode("oid123,stu001,wxcode");
        assertThat(success.status).isEqualTo("SUCCESS");
        assertThat(success.openId).isEqualTo("oid123");
        assertThat(success.designatedUsername).isEqualTo("stu001");
    }
}
