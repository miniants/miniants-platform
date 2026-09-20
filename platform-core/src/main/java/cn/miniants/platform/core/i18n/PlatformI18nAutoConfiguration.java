package cn.miniants.platform.core.i18n;

import cn.miniants.platform.core.error.ErrorMessages;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 中性 i18n：错误文案。Servlet {@code LocaleResolver} 在 {@code platform-core-webmvc}。
 */
@AutoConfiguration
@EnableConfigurationProperties(PlatformI18nProperties.class)
public class PlatformI18nAutoConfiguration {

    /**
     * 应用侧文案直接用 ApplicationContext——它本身就是 MessageSource，会转发到 Boot 装配的
     * {@code messageSource}。按类型注入会在应用自定义多个 MessageSource 时炸掉。
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorMessages platformErrorMessages(ApplicationContext applicationContext,
            PlatformI18nProperties properties) {
        ResourceBundleMessageSource platformMessages = new ResourceBundleMessageSource();
        platformMessages.setDefaultEncoding("UTF-8");
        platformMessages.setBasenames(properties.getBasenames().toArray(String[]::new));
        platformMessages.setDefaultLocale(properties.getDefaultLocale());
        // 缺哪种语言就退到 defaultLocale，别退到部署机器的 LANG
        platformMessages.setFallbackToSystemLocale(false);
        return new ErrorMessages(applicationContext, platformMessages);
    }
}
