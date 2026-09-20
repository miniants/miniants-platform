package cn.miniants.platform.core.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "platform.core.i18n")
public class PlatformI18nProperties {

    /**
     * 内核错误文案的资源包。项目要加自己的错误码文案时往这里追加 basename，
     * 不要去改内核自带的那份。
     */
    private List<String> basenames = new ArrayList<>(List.of("messages/platform-errors"));

    /**
     * 请求没带 {@code Accept-Language} 时按哪种语言回话。
     *
     * <p>不设的话 Spring 会退到 JVM 默认 locale，于是同一份代码在 {@code LANG=en_US}
     * 的机器上会把中文用户的报错全变成英文。默认锁成中文，要多语言的项目再改。
     */
    private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;

    /** 关掉后不再托管 {@code localeResolver}，由应用自己决定语言协商。 */
    private boolean localeResolverEnabled = true;

    public List<String> getBasenames() {
        return basenames;
    }

    public void setBasenames(List<String> basenames) {
        this.basenames = basenames;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }

    public boolean isLocaleResolverEnabled() {
        return localeResolverEnabled;
    }

    public void setLocaleResolverEnabled(boolean localeResolverEnabled) {
        this.localeResolverEnabled = localeResolverEnabled;
    }
}
