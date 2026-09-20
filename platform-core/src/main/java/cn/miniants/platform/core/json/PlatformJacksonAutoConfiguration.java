package cn.miniants.platform.core.json;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

/**
 * MVC {@code JsonMapper}：JS 安全 Long、中文日期读写。Redis / JWT 等自建 mapper 不受影响。
 */
@AutoConfiguration
@ConditionalOnClass(JsonMapperBuilderCustomizer.class)
public class PlatformJacksonAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.core.json", name = "long-as-string", havingValue = "true",
            matchIfMissing = true)
    public JsonMapperBuilderCustomizer platformJsSafeLongCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("platform-js-safe-long");
            module.addSerializer(Long.class, JsSafeLongSerializer.INSTANCE);
            module.addSerializer(Long.TYPE, JsSafeLongSerializer.INSTANCE);
            builder.addModule(module);
        };
    }

    @Bean
    public JsonMapperBuilderCustomizer platformDateTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("platform-datetime");
            module.addSerializer(LocalDateTime.class,
                    new PlatformLocalDateTimeSerializer(PlatformDateTimeFormats.DATE_TIME_FORMATTER));
            module.addDeserializer(LocalDateTime.class, FlexibleLocalDateTimeDeserializer.INSTANCE);
            builder.addModule(module);
        };
    }
}
