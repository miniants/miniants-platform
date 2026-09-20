package cn.miniants.platform.ratelimit.admin.snapshot;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import cn.miniants.platform.ratelimit.admin.dto.RateLimitPolicyVo;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略全集快照 JSON 编解码（管理端修订与 Redis 共用）。
 */
public class RateLimitPolicySnapshotCodec {

    private final ObjectMapper objectMapper;

    public RateLimitPolicySnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(long revision, List<RateLimitPolicyVo> policies) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("revision", revision);
            ArrayNode array = root.putArray("policies");
            for (RateLimitPolicyVo policy : policies) {
                ObjectNode node = array.addObject();
                if (policy.getId() != null) {
                    node.put("id", policy.getId());
                }
                node.put("policyCode", policy.getPolicyCode());
                node.put("algorithm", policy.getAlgorithm());
                node.put("limitCount", policy.getLimitCount());
                node.put("periodMs", policy.getPeriodMs());
                node.put("burst", policy.getBurst());
                node.put("storeFailurePolicy", policy.getStoreFailurePolicy());
                node.put("enabled", Boolean.TRUE.equals(policy.getEnabled()));
                node.put("version", policy.getVersion() == null ? 0L : policy.getVersion());
                if (policy.getRemark() != null) {
                    node.put("remark", policy.getRemark());
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException ex) {
            throw new PlatformException("序列化限流策略快照失败", ex);
        }
    }

    public Snapshot decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            long revision = root.path("revision").asLong(0L);
            List<RateLimitPolicyVo> policies = new ArrayList<>();
            JsonNode array = root.path("policies");
            if (array.isArray()) {
                for (JsonNode node : array) {
                    RateLimitPolicyVo vo = new RateLimitPolicyVo();
                    if (node.hasNonNull("id")) {
                        vo.setId(node.get("id").asLong());
                    }
                    vo.setPolicyCode(text(node, "policyCode"));
                    vo.setAlgorithm(text(node, "algorithm"));
                    vo.setLimitCount(node.path("limitCount").asLong());
                    vo.setPeriodMs(node.path("periodMs").asLong());
                    vo.setBurst(node.path("burst").asLong());
                    vo.setStoreFailurePolicy(text(node, "storeFailurePolicy"));
                    vo.setEnabled(node.path("enabled").asBoolean(true));
                    vo.setVersion(node.path("version").asLong(0L));
                    if (node.has("remark") && !node.get("remark").isNull()) {
                        vo.setRemark(node.get("remark").asString());
                    }
                    policies.add(vo);
                }
            }
            return new Snapshot(revision, policies);
        } catch (Exception ex) {
            throw new PlatformException("解析限流策略快照失败", ex);
        }
    }

    public Map<String, RateLimitPolicy> toRuntimeMap(List<RateLimitPolicyVo> policies) {
        Map<String, RateLimitPolicy> map = new LinkedHashMap<>();
        if (policies == null) {
            return map;
        }
        for (RateLimitPolicyVo vo : policies) {
            RateLimitPolicy policy = toRuntimePolicy(vo);
            map.put(policy.policyCode(), policy);
        }
        return map;
    }

    public RateLimitPolicy toRuntimePolicy(RateLimitPolicyVo vo) {
        RateLimitAlgorithm algorithm = RateLimitAlgorithm.valueOf(vo.getAlgorithm());
        StoreFailurePolicy failurePolicy = StoreFailurePolicy.valueOf(vo.getStoreFailurePolicy());
        long burst = vo.getBurst() == null ? vo.getLimitCount() : vo.getBurst();
        return RateLimitPolicy.builder()
                .policyCode(vo.getPolicyCode())
                .algorithm(algorithm)
                .limit(vo.getLimitCount())
                .period(Duration.ofMillis(vo.getPeriodMs()))
                .burst(burst)
                .storeFailurePolicy(failurePolicy)
                .enabled(Boolean.TRUE.equals(vo.getEnabled()))
                .version(vo.getVersion() == null ? 0L : vo.getVersion())
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public record Snapshot(long revision, List<RateLimitPolicyVo> policies) {
    }
}
