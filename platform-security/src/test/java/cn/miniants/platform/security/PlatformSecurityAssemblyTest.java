package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSecurityAssemblyTest {

    @Test
    void assemblesOnePlatformPermissionAndOperLogInterceptor() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformSecurityAutoConfiguration.class))
                .withBean(OperLogRecorder.class, () -> entry -> {
                })
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(PermissionInterceptor.class)
                            .hasSingleBean(OperLogInterceptor.class)
                            .hasSingleBean(OwnedInterceptor.class)
                            .hasSingleBean(OwnedAnnotationRegistrar.class)
                            .hasSingleBean(PublicAccessPaths.class)
                            .hasSingleBean(PublicAccessPathRegistrar.class);
                    PublicAccessPaths paths = context.getBean(PublicAccessPaths.class);
                    assertThat(paths.matches("/oauth/token")).isTrue();
                    assertThat(paths.matches("/actuator/health")).isTrue();
                    assertThat(paths.matches("/internal/runtime/state")).isTrue();
                });
    }
}
