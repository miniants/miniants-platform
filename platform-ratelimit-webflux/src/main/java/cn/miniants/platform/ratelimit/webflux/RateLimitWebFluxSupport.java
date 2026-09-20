package cn.miniants.platform.ratelimit.webflux;

import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitSubjectType;
import cn.miniants.platform.ratelimit.annotation.RateLimit;
import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import cn.miniants.platform.ratelimit.client.ClientAddressResolver;
import cn.miniants.platform.ratelimit.spi.RateLimitPolicyRegistry;
import cn.miniants.platform.ratelimit.spi.RateLimitSubjectResolver;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebFlux 限流：注解扫描、主体与策略解析。
 */
public final class RateLimitWebFluxSupport {

    public static final String EXCHANGE_CONTEXT_KEY = RateLimitWebFluxSupport.class.getName() + ".EXCHANGE";

    private static final String CURRENT_USER_CLASS = "cn.miniants.platform.security.CurrentUser";

    private final ConcurrentHashMap<HandlerMethod, List<RateLimitAnnotationBinding>> cache =
            new ConcurrentHashMap<>();

    private final RateLimitPolicyRegistry policyRegistry;
    private final ClientAddressResolver clientAddressResolver;
    private final BeanFactory beanFactory;

    public RateLimitWebFluxSupport(
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

    public String resolveSubject(RateLimitAnnotationBinding binding, ServerWebExchange exchange) {
        return switch (binding.subjectType()) {
            case CLIENT_IP -> resolveClientIp(exchange);
            case USER_ID -> resolveUserId(exchange);
            case CUSTOM -> resolveCustom(binding.resolverBeanName(), exchange);
        };
    }

    public void validateBindings(List<RateLimitAnnotationBinding> bindings) {
        validateBindings(bindings, -50);
    }

    public void validateBindings(List<RateLimitAnnotationBinding> bindings, int filterOrder) {
        for (RateLimitAnnotationBinding binding : bindings) {
            if (binding.namedPolicy() && policyRegistry.find(binding.policyCode()).isEmpty()) {
                throw new IllegalStateException(
                        "未找到限流策略 '" + binding.policyCode() + "': " + binding.location());
            }
            if (binding.subjectType() == RateLimitSubjectType.USER_ID) {
                if (!ClassUtils.isPresent(CURRENT_USER_CLASS, getClass().getClassLoader())) {
                    throw new IllegalStateException(
                            "USER_ID 主体需要 classpath 上的 platform-security CurrentUser: "
                                    + binding.location());
                }
                if (filterOrder <= -100) {
                    throw new IllegalStateException(
                            "USER_ID 绑定要求限流过滤器在鉴权之后执行，当前 order="
                                    + filterOrder + ": " + binding.location());
                }
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

    private String resolveClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String remote = remoteAddress(request);
        String forwarded = request.getHeaders().getFirst("Forwarded");
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        return clientAddressResolver.resolve(remote, forwarded, xff);
    }

    private static String remoteAddress(ServerHttpRequest request) {
        InetSocketAddress addr = request.getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return "unknown";
        }
        return addr.getAddress().getHostAddress();
    }

    private String resolveUserId(ServerWebExchange exchange) {
        try {
            Class<?> type = Class.forName(CURRENT_USER_CLASS);
            String attr = (String) type.getField("REQUEST_ATTR").get(null);
            Object user = exchange.getAttribute(attr);
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

    private String resolveCustom(String beanName, ServerWebExchange exchange) {
        RateLimitSubjectResolver resolver = beanFactory.getBean(beanName, RateLimitSubjectResolver.class);
        String subject = resolver.resolve(exchange);
        if (subject == null || subject.isBlank()) {
            throw new IllegalStateException("自定义限流主体解析结果为空: " + beanName);
        }
        return subject;
    }
}
