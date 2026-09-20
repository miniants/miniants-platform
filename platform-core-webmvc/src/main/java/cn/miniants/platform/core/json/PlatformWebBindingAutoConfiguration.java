package cn.miniants.platform.core.json;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 表单 / query 的日期字符串走与 JSON 相同的中文格式，不依赖教务仓 {@code @InitBinder}。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class PlatformWebBindingAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Date.class, FlexibleDateConverter.INSTANCE);
        registry.addConverter(String.class, LocalDateTime.class, FlexibleLocalDateTimeConverter.INSTANCE);
    }
}
