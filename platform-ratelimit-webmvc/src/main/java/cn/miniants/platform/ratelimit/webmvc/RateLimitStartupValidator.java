package cn.miniants.platform.ratelimit.webmvc;

import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 启动时扫描全部 {@code @RateLimit}，校验命名策略与 CUSTOM resolver 存在。
 */
public class RateLimitStartupValidator implements SmartInitializingSingleton {

    private final Collection<RequestMappingHandlerMapping> handlerMappings;
    private final RateLimitMvcSupport support;

    public RateLimitStartupValidator(
            Collection<RequestMappingHandlerMapping> handlerMappings,
            RateLimitMvcSupport support) {
        this.handlerMappings = List.copyOf(Objects.requireNonNull(handlerMappings, "handlerMappings"));
        this.support = Objects.requireNonNull(support, "support");
    }

    public RateLimitStartupValidator(RequestMappingHandlerMapping handlerMapping, RateLimitMvcSupport support) {
        this(List.of(handlerMapping), support);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
            Map<RequestMappingInfo, HandlerMethod> map = handlerMapping.getHandlerMethods();
            for (HandlerMethod handlerMethod : map.values()) {
                List<RateLimitAnnotationBinding> bindings = support.scan(handlerMethod);
                if (bindings.isEmpty()) {
                    continue;
                }
                support.validateBindings(bindings);
                support.bindingsFor(handlerMethod);
            }
        }
    }
}
