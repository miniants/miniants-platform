package cn.miniants.platform.starter;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter 只是依赖聚合，本身不带类。这里盯住「引了 starter 到底拿到哪些自动装配」：
 * 漏掉一个模块的症状是某个能力静默不生效，在这里失败比在新项目里排查便宜得多。
 */
class StarterAutoConfigurationTest {

    private static final String IMPORTS =
            "classpath*:META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    void starterBringsInEveryKernelAutoConfiguration() throws Exception {
        List<String> declared = declaredAutoConfigurations();

        assertThat(declared).contains(
                "cn.miniants.platform.core.PlatformCoreAutoConfiguration",
                "cn.miniants.platform.core.json.PlatformJacksonAutoConfiguration",
                "cn.miniants.platform.core.json.PlatformWebBindingAutoConfiguration",
                "cn.miniants.platform.core.i18n.PlatformI18nAutoConfiguration",
                "cn.miniants.platform.data.PlatformDataAutoConfiguration",
                "cn.miniants.platform.data.id.PlatformSnowflakeAutoConfiguration",
                "cn.miniants.platform.data.flyway.PlatformFlywayAutoConfiguration",
                "cn.miniants.platform.security.PlatformSecurityAutoConfiguration",
                "cn.miniants.platform.observability.PlatformObservabilityAutoConfiguration",
                "cn.miniants.platform.observability.sql.PlatformSlowSqlAutoConfiguration",
                "cn.miniants.platform.integration.PlatformIntegrationAutoConfiguration",
                "cn.miniants.platform.integration.PlatformIntegrationRedisAutoConfiguration",
                "cn.miniants.platform.ops.PlatformOpsAutoConfiguration",
                "cn.miniants.platform.core.i18n.PlatformMvcLocaleAutoConfiguration",
                "cn.miniants.platform.ratelimit.config.PlatformRateLimitCoreAutoConfiguration",
                "cn.miniants.platform.ratelimit.webmvc.config.PlatformRateLimitWebMvcAutoConfiguration");
    }

    /** 存储和队列是按需引的：默认拉进来会让不用的项目白挂 minio / redis 依赖。 */
    @Test
    void optionalModulesAreNotDraggedIn() throws Exception {
        List<String> declared = declaredAutoConfigurations();

        assertThat(declared).doesNotContain(
                "cn.miniants.platform.storage.PlatformStorageAutoConfiguration",
                "cn.miniants.platform.queue.PlatformQueueAutoConfiguration",
                "cn.miniants.platform.admin.PlatformAdminAutoConfiguration",
                "cn.miniants.platform.tenant.PlatformTenantAutoConfiguration",
                "cn.miniants.platform.ratelimit.admin.PlatformRateLimitAdminAutoConfiguration",
                "cn.miniants.platform.core.PlatformCoreWebFluxAutoConfiguration",
                "cn.miniants.platform.ratelimit.webflux.config.PlatformRateLimitWebFluxAutoConfiguration");
    }

    @Test
    void everyDeclaredAutoConfigurationIsLoadable() throws Exception {
        for (String name : declaredAutoConfigurations()) {
            Class.forName(name, false, getClass().getClassLoader());
        }
    }

    private static List<String> declaredAutoConfigurations() throws IOException {
        List<String> names = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources(IMPORTS)) {
            try (InputStream in = resource.getInputStream()) {
                new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(names::add);
            }
        }
        return names;
    }
}
