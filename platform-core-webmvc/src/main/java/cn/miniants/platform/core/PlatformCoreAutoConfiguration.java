package cn.miniants.platform.core;

import cn.miniants.platform.core.advice.PlatformResultAdvice;
import cn.miniants.platform.core.advice.PlatformWebAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.core.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnMissingBean(PlatformWebAdvice.class)
@Import({PlatformWebAdvice.class, PlatformResultAdvice.class})
public class PlatformCoreAutoConfiguration {
}
