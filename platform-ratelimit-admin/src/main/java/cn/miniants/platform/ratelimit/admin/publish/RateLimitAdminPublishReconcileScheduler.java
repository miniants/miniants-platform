package cn.miniants.platform.ratelimit.admin.publish;

import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.ratelimit.policy.CompositeRateLimitPolicyRegistry;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 事务已提交但 Redis 发布失败时，重试最新 DB 快照，不新增修订。
 */
public class RateLimitAdminPublishReconcileScheduler {

    private final RateLimitPolicyAdminService adminService;
    private final CompositeRateLimitPolicyRegistry registry;

    public RateLimitAdminPublishReconcileScheduler(
            RateLimitPolicyAdminService adminService,
            CompositeRateLimitPolicyRegistry registry) {
        this.adminService = adminService;
        this.registry = registry;
    }

    @Scheduled(fixedDelayString = "${platform.ratelimit.refresh-interval:30s}")
    public void retryFailedPublish() {
        if (registry.publishFailed()) {
            adminService.retryPublishLatest();
        }
    }
}
