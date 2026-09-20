package cn.miniants.platform.core;

import cn.miniants.platform.core.advice.PlatformWebFluxAdvice;
import cn.miniants.platform.core.advice.PlatformWebFluxResultFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "platform.core.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(PlatformWebFluxAdvice.class)
public class PlatformCoreWebFluxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformWebFluxResultFilter.class)
    public PlatformWebFluxResultFilter platformWebFluxResultFilter(ServerCodecConfigurer codecConfigurer,
            RequestedContentTypeResolver contentTypeResolver) {
        return new PlatformWebFluxResultFilter(codecConfigurer.getWriters(), contentTypeResolver);
    }
}
