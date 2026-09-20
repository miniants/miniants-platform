package cn.miniants.platform.ratelimit.webmvc;

import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitSubjectType;
import cn.miniants.platform.ratelimit.annotation.RateLimit;
import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import cn.miniants.platform.ratelimit.client.ClientAddressResolver;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitSubjectResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MVC 限流：注解扫描、主体解析、策略解析。
 */
public final class RateLimitMvcSupport {

    private static final String CURRENT_USER_CLASS = "cn.miniants.platform.security.CurrentUser";

    private final ConcurrentHashMap<HandlerMethod, List<RateLimitAnnotationBinding>> cache =
            new ConcurrentHashMap<>();

    private final RateLimitPolicyRegistry policyRegistry;
    private final ClientAddressResolver clientAddressResolver;
    private final BeanFactory beanFactory;

    public RateLimitMvcSupport(
            RateLimitPolicyRegistry policyRegistry,
            ClientAddressResolver clientAddressResolver,
            BeanFactory beanFactory) {
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry");
        this.clientAddressResolver = Objects.requireNonNull(clientAddressResolver, "clientAddressResolver");
        this.beanFactory = Objects.requireNonNull(beanFactory, "beanFactory");
    }

    public List<RateLimitAnnotationBinding> bindingsFor(HandlerMethod handlerMethod) {
        return cache.computeIfAbsent(handlerMethod, this::scan);
    }

    public List<RateLimitAnnotationBinding> scan(HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        List<RateLimit> annotations = new ArrayList<>();
        annotations.addAll(AnnotatedElementUtils.findMergedRepeatableAnnotations(beanType, RateLimit.class));
        annotations.addAll(AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class));
        if (annotations.isEmpty()) {
            return List.of();
        }
        String location = beanType.getSimpleName() + "#" + method.getName();
        List<RateLimitAnnotationBinding> bindings = new ArrayList<>(annotations.size());
        int index = 0;
        for (RateLimit annotation : annotations) {
            String synthetic = "@RateLimit:" + beanType.getName() + "#" + method.getName() + "[" + index + "]";
            bindings.add(RateLimitAnnotationBinding.parse(annotation, location, synthetic));
            index++;
        }
        return List.copyOf(bindings);
    }

    public RateLimitPolicy resolvePolicy(RateLimitAnnotationBinding binding) {
        if (!binding.namedPolicy()) {
            return binding.inlinePolicy();
        }
        return policyRegistry.find(binding.policyCode())
                .orElseThrow(() -> new IllegalStateException(
                        "未找到限流策略 '" + binding.policyCode() + "': " + binding.location()));
    }

    public String resolveSubject(RateLimitAnnotationBinding binding, HttpServletRequest request) {
        return switch (binding.subjectType()) {
            case CLIENT_IP -> resolveClientIp(request);
            case USER_ID -> resolveUserId(request);
            case CUSTOM -> resolveCustom(binding.resolverBeanName(), request);
        };
    }

    public void validateBindings(List<RateLimitAnnotationBinding> bindings) {
        for (RateLimitAnnotationBinding binding : bindings) {
            if (binding.namedPolicy() && policyRegistry.find(binding.policyCode()).isEmpty()) {
                throw new IllegalStateException(
                        "未找到限流策略 '" + binding.policyCode() + "': " + binding.location());
            }
            if (binding.subjectType() == RateLimitSubjectType.CUSTOM) {
                if (!beanFactory.containsBean(binding.resolverBeanName())) {
                    throw new IllegalStateException(
                            "未找到限流主体解析器 Bean '" + binding.resolverBeanName() + "': "
                                    + binding.location());
                }
                Object bean = beanFactory.getBean(binding.resolverBeanName());
                if (!(bean instanceof RateLimitSubjectResolver)) {
                    throw new IllegalStateException(
                            "Bean '" + binding.resolverBeanName() + "' 不是 RateLimitSubjectResolver: "
                                    + binding.location());
                }
            }
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("Forwarded");
        String xff = request.getHeader("X-Forwarded-For");
        return clientAddressResolver.resolve(request.getRemoteAddr(), forwarded, xff);
    }

    private String resolveUserId(HttpServletRequest request) {
        try {
            Class<?> type = Class.forName(CURRENT_USER_CLASS);
            Object user = type.getMethod("from", HttpServletRequest.class).invoke(null, request);
            if (user == null) {
                return "anonymous";
            }
            Object userId = type.getMethod("userId").invoke(user);
            if (userId != null) {
                return String.valueOf(userId);
            }
            Object username = type.getMethod("username").invoke(user);
            if (username != null && !String.valueOf(username).isBlank()) {
                return String.valueOf(username);
            }
            return "anonymous";
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            throw new IllegalStateException("USER_ID 主体需要 classpath 上的 platform-security CurrentUser", ex);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("解析当前用户失败", ex);
        }
    }

    private String resolveCustom(String beanName, HttpServletRequest request) {
        RateLimitSubjectResolver resolver = beanFactory.getBean(beanName, RateLimitSubjectResolver.class);
        String subject = resolver.resolve(request);
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("自定义限流主体解析结果为空: " + beanName);
        }
        return subject;
    }
}
