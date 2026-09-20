package cn.miniants.platform.ratelimit.webmvc;

import cn.miniants.platform.ratelimit.RateLimitDecision;
import cn.miniants.platform.ratelimit.RateLimitExceededException;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.RateLimitRequest;
import cn.miniants.platform.ratelimit.annotation.RateLimitAnnotationBinding;
import cn.miniants.platform.ratelimit.spi.RateLimiter;
import cn.miniants.platform.ratelimit.support.RateLimitHttpHeaders;
import cn.miniants.platform.ratelimit.support.RateLimitRejectLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;

/**
 * 在 Handler 执行前申请限流额度；拒绝时抛 {@link RateLimitExceededException}。
 */
public class RateLimitHandlerInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final RateLimitMvcSupport support;

    public RateLimitHandlerInterceptor(RateLimiter rateLimiter, RateLimitMvcSupport support) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.support = Objects.requireNonNull(support, "support");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        List<RateLimitAnnotationBinding> bindings = support.bindingsFor(handlerMethod);
        if (bindings.isEmpty()) {
            return true;
        }
        for (RateLimitAnnotationBinding binding : bindings) {
            RateLimitPolicy policy = support.resolvePolicy(binding);
            if (!policy.enabled()) {
                continue;
            }
            String subject = support.resolveSubject(binding, request);
            RateLimitRequest rateLimitRequest = RateLimitRequest.of(policy.policyCode(), subject, binding.cost());
            RateLimitDecision decision = rateLimiter.acquire(rateLimitRequest, policy);
            if (!decision.allowed()) {
                stamp(request, decision);
                RateLimitRejectLogger.denied(decision, subject);
                throw new RateLimitExceededException(decision);
            }
        }
        return true;
    }

    private static void stamp(HttpServletRequest request, RateLimitDecision decision) {
        request.setAttribute(RateLimitHttpHeaders.ACCESS_ERR_ATTR, "RateLimitExceeded");
        request.setAttribute(
                RateLimitHttpHeaders.ACCESS_REASON_ATTR,
                "rate-limit:" + decision.policyCode() + ":" + decision.outcome());
    }
}
