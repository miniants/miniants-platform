package cn.miniants.platform.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderTenantAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformTenantAutoConfiguration.class))
            .withPropertyValues(
                    "platform.tenant.enabled=true",
                    "platform.tenant.header-bind=true");

    @Test
    void headerBindingRequiresExplicitTrustedResolver() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void adopterProvidedResolverEnablesHeaderBinding() {
        runner.withBean(TenantResolver.class, () -> new HeaderTenantResolver("X-Trusted-Tenant"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("headerTenantFilter");
                });
    }
}
