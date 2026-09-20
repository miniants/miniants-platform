package cn.miniants.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.AnnotatedElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动后扫描 {@link PublicAccess} mapping，写入 {@link PublicAccessPaths}。
 */
public class PublicAccessPathRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PublicAccessPathRegistrar.class);

    private final PublicAccessPaths publicAccessPaths;
    private final List<RequestMappingHandlerMapping> handlerMappings;

    public PublicAccessPathRegistrar(PublicAccessPaths publicAccessPaths,
                                     List<RequestMappingHandlerMapping> handlerMappings) {
        this.publicAccessPaths = publicAccessPaths;
        this.handlerMappings = handlerMappings == null ? List.of() : handlerMappings;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void refresh() {
        Set<String> patterns = new LinkedHashSet<>();
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            mapping.getHandlerMethods().forEach((info, method) -> {
                if (annotated(method)) {
                    patterns.addAll(patternValues(info));
                }
            });
        }
        publicAccessPaths.replace(patterns);
        log.info("public-access mappings: {}", patterns);
    }

    public static boolean annotated(HandlerMethod handler) {
        return annotated(handler.getMethod()) || annotated(handler.getBeanType());
    }

    public static boolean annotated(AnnotatedElement element) {
        return element.getAnnotation(PublicAccess.class) != null;
    }

    static Set<String> patternValues(RequestMappingInfo info) {
        Set<String> result = new LinkedHashSet<>();
        if (info.getPathPatternsCondition() != null) {
            result.addAll(info.getPathPatternsCondition().getPatternValues());
        }
        return result;
    }
}
