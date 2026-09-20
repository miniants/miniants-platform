package cn.miniants.platform.core.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

/**
 * Servlet 语言解析。Boot 默认 {@code localeResolver} 在无 Accept-Language 时会落到 JVM LANG。
 */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class PlatformMvcLocaleAutoConfiguration {

    @Bean(DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
    @ConditionalOnProperty(prefix = "platform.core.i18n", name = "locale-resolver-enabled",
            havingValue = "true", matchIfMissing = true)
    public LocaleResolver platformLocaleResolver(PlatformI18nProperties properties, Environment environment) {
        Locale locale = parseLocale(environment.getProperty("spring.web.locale"), properties.getDefaultLocale());
        if ("fixed".equalsIgnoreCase(environment.getProperty("spring.web.locale-resolver"))) {
            return new FixedLocaleResolver(locale);
        }
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(locale);
        return resolver;
    }

    private static Locale parseLocale(String raw, Locale fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Locale.forLanguageTag(raw.replace('_', '-'));
    }
}
