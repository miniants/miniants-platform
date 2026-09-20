package cn.miniants.platform.admin.keystore;

import cn.miniants.platform.admin.dto.JwtKeystoreVo;
import cn.miniants.platform.admin.dto.JwtWrapSettingsVo;
import cn.miniants.platform.admin.service.DefaultJwtKeystoreAdminService;
import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.data.PlatformDataProperties;
import cn.miniants.platform.security.sas.PlatformSasProperties;
import cn.miniants.platform.security.sas.keystore.ClasspathSingleKeyStore;
import cn.miniants.platform.security.sas.keystore.JwtKeyEntry;
import cn.miniants.platform.security.sas.keystore.RotatableJwkSource;
import org.flywaydb.core.Flyway;
import org.h2.Driver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeystoreAdminServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AesGcmPrivateKeyWrap wrap;
    private JdbcJwtKeyStore store;
    private RotatableJwkSource jwkSource;
    private DefaultJwtKeystoreAdminService service;
    private TransactionTemplate tx;
    private KeyPair fallbackPair;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:jwt_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        wrap = AesGcmPrivateKeyWrap.parse(AesGcmPrivateKeyWrapTest.randomKey());
        PlatformDataProperties dataProperties = new PlatformDataProperties();
        PlatformSasProperties sasProperties = new PlatformSasProperties();
        sasProperties.setKidPrefix("jwt-test");
        sasProperties.getKeystore().setAlias("jwt");
        fallbackPair = rsa();
        store = new JdbcJwtKeyStore(jdbcTemplate, wrap, dataProperties, fallbackPair, sasProperties);
        jwkSource = new RotatableJwkSource(store, new ClasspathSingleKeyStore(fallbackPair, "jwt"));
        service = new DefaultJwtKeystoreAdminService(
                null,
                jdbcTemplate,
                wrap,
                dataProperties,
                sasProperties,
                fallbackPair,
                jwkSource,
                event -> {
                });
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void missingTableFallsBackToClasspathSigning() {
        DataSource dataSource = new SimpleDriverDataSource(
                new Driver(),
                "jdbc:h2:mem:jwt_missing_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate bare = new JdbcTemplate(dataSource);
        PlatformDataProperties dataProperties = new PlatformDataProperties();
        PlatformSasProperties sasProperties = new PlatformSasProperties();
        sasProperties.getKeystore().setAlias("jwt");
        JdbcJwtKeyStore bareStore = new JdbcJwtKeyStore(
                bare, wrap, dataProperties, fallbackPair, sasProperties);
        RotatableJwkSource source = new RotatableJwkSource(
                bareStore, new ClasspathSingleKeyStore(fallbackPair, "jwt"));

        assertThat(bareStore.list()).isEmpty();
        assertThat(bareStore.loadActive()).isNull();
        assertThat(source.activeKid()).isEqualTo("jwt");
    }

    @Test
    void emptyStoreReturnsEmptyListAndKeepsClasspathSigning() {
        assertThat(store.list()).isEmpty();
        assertThat(store.loadActive()).isNull();
        assertThat(jwkSource.activeKid()).isEqualTo("jwt");
    }

    @Test
    void generateThenActivateSwitchesSigningKid() {
        assertThat(jwkSource.activeKid()).isEqualTo("jwt");

        JwtKeystoreVo generated = tx.execute(status -> service.generate());
        assertThat(generated).isNotNull();
        assertThat(generated.getKid()).startsWith("jwt-test-");
        assertThat(generated.getStatus()).isEqualTo(JwtKeystoreStatuses.VERIFY_ONLY);
        assertThat(generated.isActive()).isFalse();
        assertThat(generated.getFingerprint()).isNotBlank();
        assertThat(jwkSource.activeKid()).isEqualTo("jwt");
        assertThat(store.list().stream().map(JwtKeyEntry::kid)).contains(generated.getKid());

        JwtKeystoreVo activated = tx.execute(status -> service.activate(generated.getKid()));
        assertThat(activated).isNotNull();
        assertThat(activated.isActive()).isTrue();
        assertThat(activated.getStatus()).isEqualTo(JwtKeystoreStatuses.ACTIVE);
        assertThat(jwkSource.activeKid()).isEqualTo(generated.getKid());
        assertThat(store.loadActive().kid()).isEqualTo(generated.getKid());
        assertThat(store.loadActive().hasPrivateKey()).isTrue();
    }

    @Test
    void cannotRetireActiveAndRetireKeepsCipher() {
        JwtKeystoreVo generated = tx.execute(status -> service.generate());
        assertThat(generated).isNotNull();
        String kid = generated.getKid();
        tx.executeWithoutResult(status -> service.activate(kid));

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.retire(kid)))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("不能摘除当前签发密钥");

        JwtKeystoreVo second = tx.execute(status -> service.generate());
        assertThat(second).isNotNull();
        tx.executeWithoutResult(status -> service.activate(second.getKid()));

        JwtKeystoreVo retired = tx.execute(status -> service.retire(kid));
        assertThat(retired.getStatus()).isEqualTo(JwtKeystoreStatuses.RETIRED);
        assertThat(retired.isActive()).isFalse();
        assertThat(retired.getRetiredAt()).isNotNull();

        String cipher = jdbcTemplate.queryForObject(
                "SELECT private_key_cipher FROM sys_jwt_keystore WHERE kid = ?",
                String.class,
                kid);
        assertThat(cipher).isNotBlank();
        assertThat(store.list().stream().map(JwtKeyEntry::kid)).doesNotContain(kid);
        assertThat(jwkSource.activeKid()).isEqualTo(second.getKid());
    }

    @Test
    void listNeverExposesPrivateKeyMaterial() {
        tx.executeWithoutResult(status -> service.generate());
        List<JwtKeystoreVo> vos = service.list();
        assertThat(vos).isNotEmpty();
        String json = Jsons.toJson(vos);
        assertThat(json).doesNotContain("private");
        assertThat(json).doesNotContain("BEGIN PRIVATE");
        assertThat(vos).allSatisfy(vo -> {
            assertThat(vo.getKid()).isNotBlank();
            assertThat(vo.getFingerprint()).isNotBlank();
        });
    }

    @Test
    void databaseWrapSourceAutoCreatesKeyAndDoesNotEchoSecret() {
        PlatformJwtKeystoreProperties properties = new PlatformJwtKeystoreProperties();
        properties.setWrapSource(JwtWrapSources.DATABASE);
        properties.setWrapKey("");
        PlatformDataProperties dataProperties = new PlatformDataProperties();
        JwtWrapKeyResolver resolver = new JwtWrapKeyResolver(jdbcTemplate, dataProperties, properties);
        AesGcmPrivateKeyWrap dbWrap = AesGcmPrivateKeyWrap.from(properties, resolver);
        DefaultJwtKeystoreAdminService dbService = new DefaultJwtKeystoreAdminService(
                null,
                jdbcTemplate,
                dbWrap,
                dataProperties,
                new PlatformSasProperties(),
                fallbackPair,
                jwkSource,
                event -> {
                },
                resolver);

        JwtWrapSettingsVo before = dbService.wrapSettings();
        assertThat(before.getSource()).isEqualTo(JwtWrapSources.DATABASE);
        assertThat(before.isDatabaseReady()).isFalse();

        JwtKeystoreVo generated = tx.execute(status -> dbService.generate());
        assertThat(generated).isNotNull();
        assertThat(generated.getKid()).isNotBlank();

        JwtWrapSettingsVo after = dbService.saveWrapSource(JwtWrapSources.DATABASE);
        assertThat(after.isDatabaseReady()).isTrue();
        String json = Jsons.toJson(after);
        assertThat(json).doesNotContain("wrapKey");
        assertThat(json).doesNotContain("wrap_key");
        String stored = jdbcTemplate.queryForObject(
                "SELECT wrap_key FROM sys_jwt_wrap_settings WHERE id = 1",
                String.class);
        assertThat(stored).isNotBlank();
        assertThat(json).doesNotContain(stored);
    }

    @Test
    void missingWrapKeyRefusesGenerateActivateRetire() {
        AesGcmPrivateKeyWrap missing = AesGcmPrivateKeyWrap.parse("");
        PlatformDataProperties dataProperties = new PlatformDataProperties();
        DefaultJwtKeystoreAdminService blocked = new DefaultJwtKeystoreAdminService(
                null,
                jdbcTemplate,
                missing,
                dataProperties,
                new PlatformSasProperties(),
                fallbackPair,
                jwkSource,
                event -> {
                });

        assertThatThrownBy(blocked::generate)
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> blocked.activate("any"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("未配置");
        assertThatThrownBy(() -> blocked.retire("any"))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("未配置");
        assertThat(store.list()).isEmpty();
        assertThat(jwkSource.activeKid()).isEqualTo("jwt");
    }

    @Test
    void canReactivateRetiredKid() {
        JwtKeystoreVo first = tx.execute(status -> service.generate());
        JwtKeystoreVo second = tx.execute(status -> service.generate());
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        tx.executeWithoutResult(status -> service.activate(first.getKid()));
        tx.executeWithoutResult(status -> service.activate(second.getKid()));
        tx.executeWithoutResult(status -> service.retire(first.getKid()));

        JwtKeystoreVo rolledBack = tx.execute(status -> service.activate(first.getKid()));
        assertThat(rolledBack.isActive()).isTrue();
        assertThat(jwkSource.activeKid()).isEqualTo(first.getKid());
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
