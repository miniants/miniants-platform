package cn.miniants.platform.ratelimit.admin.web;

import cn.miniants.platform.ratelimit.admin.RateLimitBucketHistory;
import cn.miniants.platform.ratelimit.admin.dto.PageResult;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitBucketsVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyRevisionVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicySave;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitRuntimeVo;
import cn.miniants.platform.ratelimit.admin.service.RateLimitPolicyAdminService;
import cn.miniants.platform.security.OperLog;
import cn.miniants.platform.security.OperLogEventTypes;
import cn.miniants.platform.security.Permission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/platform/admin/rate-limit/policies")
public class RateLimitPolicyAdminController {

    private final RateLimitPolicyAdminService adminService;
    private final RateLimitBucketHistory bucketHistory;

    public RateLimitPolicyAdminController(
            RateLimitPolicyAdminService adminService,
            ObjectProvider<RateLimitBucketHistory> bucketHistory) {
        this.adminService = adminService;
        this.bucketHistory = bucketHistory.getIfAvailable();
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/page")
    public PageResult<RateLimitPolicyVo> page(
            @RequestParam(name = "current", required = false) Long current,
            @RequestParam(name = "size", required = false) Long size,
            @RequestParam(name = "policyCode", required = false) String policyCode) {
        return adminService.page(current, size, policyCode);
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/{id}")
    public RateLimitPolicyVo get(@PathVariable("id") Long id) {
        return adminService.get(id);
    }

    @Permission("platform:ratelimit:save")
    @OperLog(value = "保存限流策略", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/save")
    public RateLimitPolicyVo save(@RequestBody RateLimitPolicySave body) {
        return adminService.save(body);
    }

    @Permission("platform:ratelimit:save")
    @OperLog(value = "启用限流策略", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/{id}/enable")
    public RateLimitPolicyVo enable(@PathVariable("id") Long id) {
        return adminService.enable(id);
    }

    @Permission("platform:ratelimit:save")
    @OperLog(value = "停用限流策略", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/{id}/disable")
    public RateLimitPolicyVo disable(@PathVariable("id") Long id) {
        return adminService.disable(id);
    }

    @Permission("platform:ratelimit:publish")
    @OperLog(value = "重新发布限流策略", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/republish")
    public Map<String, Long> republish() {
        return Map.of("revision", adminService.republish());
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/revisions")
    public List<RateLimitPolicyRevisionVo> revisions(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return adminService.listRevisions(limit);
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/revisions/{revision}")
    public RateLimitPolicyRevisionVo revision(@PathVariable("revision") Long revision) {
        return adminService.getRevision(revision);
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/revisions/{revision}/diff")
    public Map<String, Object> revisionDiff(
            @PathVariable("revision") Long revision,
            @RequestParam(name = "against") Long against) {
        return adminService.revisionDiff(against, revision);
    }

    @Permission("platform:ratelimit:publish")
    @OperLog(value = "回滚限流策略修订", eventType = OperLogEventTypes.CONFIG_CHANGE)
    @PostMapping("/revisions/{revision}/rollback")
    public Map<String, Long> rollback(@PathVariable("revision") Long revision) {
        return Map.of("revision", adminService.rollback(revision));
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/runtime")
    public RateLimitRuntimeVo runtime() {
        return adminService.runtime();
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/buckets")
    public RateLimitBucketsVo buckets(
            @RequestParam(name = "limit", required = false) Integer limit) {
        return adminService.buckets(limit);
    }

    @Permission("platform:ratelimit:page")
    @GetMapping("/buckets/history")
    public List<RateLimitBucketsVo> bucketHistory() {
        return bucketHistory == null ? List.of() : bucketHistory.list();
    }
}
