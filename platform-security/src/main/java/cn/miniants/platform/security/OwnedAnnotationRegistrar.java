package cn.miniants.platform.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.AnnotatedElement;
import java.util.List;

/**
 * 启动校验 {@code @Authenticated(resolver != None)} 的 mapping：
 * <ol>
 *   <li>resolver Bean 必须存在——缺失会在请求期 500，不如启动就报；</li>
 *   <li>与 {@link Permission} 互斥（同一方法只标一档），同标说明标注错误，@Permission 会被静默忽略。</li>
 * </ol>
 * {@code @PublicAccess} 允许与 resolver 并存（公开优先，不校验）。
 */
public class OwnedAnnotationRegistrar {

    private static final Logger log = LoggerFactory.getLogger(OwnedAnnotationRegistrar.class);

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final BeanFactory beanFactory;

    public OwnedAnnotationRegistrar(List<RequestMappingHandlerMapping> handlerMappings, BeanFactory beanFactory) {
        this.handlerMappings = handlerMappings == null ? List.of() : handlerMappings;
        this.beanFactory = beanFactory;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void validate() {
        for (RequestMappingHandlerMapping mapping : handlerMappings) {
            mapping.getHandlerMethods().forEach((info, method) -> validate(method));
        }
    }

    private void validate(HandlerMethod handler) {
        Authenticated owned = OwnedInterceptor.ownedAnnotation(handler);
        if (owned == null || owned.resolver() == OwnedResolver.None.class) {
            return;
        }
        if (PublicAccessPathRegistrar.annotated(handler)) {
            return;
        }
        if (hasPermission(handler.getMethod()) || hasPermission(handler.getBeanType())) {
            throw new IllegalStateException(
                    "@Authenticated(resolver=...) 与 @Permission 互斥（同一方法只标一档）: " + handler.getMethod());
        }
        Class<? extends OwnedResolver> resolverType = owned.resolver();
        try {
            beanFactory.getBean(resolverType);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException(
                    "@Authenticated resolver Bean 不存在: " + resolverType.getName() + "（" + handler.getMethod() + "）", e);
        }
    }

    private static boolean hasPermission(AnnotatedElement element) {
        return element.getAnnotation(Permission.class) != null;
    }
}
