package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderAuthAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformSecurityAutoConfiguration.class))
            .withPropertyValues("platform.security.header-auth=true");

    @Test
    void testClasspathCanEnableHeaderAuthentication() {
        runner.run(context -> assertThat(context).hasBean("headerCurrentUserFilter"));
    }

    @Test
    void productionClasspathCannotEnableHeaderAuthentication() {
        runner.withClassLoader(new FilteredClassLoader(
                        "org.springframework.boot.test.context.SpringBootTest"))
                .run(context -> assertThat(context).doesNotHaveBean("headerCurrentUserFilter"));
    }
}
