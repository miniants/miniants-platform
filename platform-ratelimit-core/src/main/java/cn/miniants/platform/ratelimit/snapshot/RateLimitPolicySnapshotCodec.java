package cn.miniants.platform.ratelimit.snapshot;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据面快照 JSON。忽略管理端多余字段。
 */
public class RateLimitPolicySnapshotCodec {

    private final ObjectMapper objectMapper;

    public RateLimitPolicySnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(long revision, Collection<RateLimitPolicy> policies) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("revision", revision);
            ArrayNode array = root.putArray("policies");
            if (policies != null) {
                for (RateLimitPolicy policy : policies) {
                    if (policy == null) {
                        continue;
                    }
                    ObjectNode node = array.addObject();
                    node.put("policyCode", policy.policyCode());
                    node.put("algorithm", policy.algorithm().name());
                    node.put("limit", policy.limit());
                    node.put("limitCount", policy.limit());
                    node.put("periodMs", policy.period().toMillis());
                    node.put("burst", policy.burst());
                    node.put("storeFailurePolicy", policy.storeFailurePolicy().name());
                    node.put("enabled", policy.enabled());
                    node.put("version", policy.version());
                }
            }
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException ex) {
            throw new PlatformException("序列化限流策略快照失败", ex);
        }
    }

    public RateLimitPolicySnapshot decode(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            long revision = root.path("revision").asLong(0L);
            List<RateLimitPolicy> policies = new ArrayList<>();
            JsonNode array = root.path("policies");
            if (array.isArray()) {
                for (JsonNode node : array) {
                    policies.add(toPolicy(node));
                }
            }
            return new RateLimitPolicySnapshot(revision, policies);
        } catch (RuntimeException ex) {
            throw new PlatformException("解析限流策略快照失败", ex);
        }
    }

    public Map<String, RateLimitPolicy> toMap(RateLimitPolicySnapshot snapshot) {
        Map<String, RateLimitPolicy> map = new LinkedHashMap<>();
        if (snapshot == null) {
            return map;
        }
        for (RateLimitPolicy policy : snapshot.policies()) {
            map.put(policy.policyCode(), policy);
        }
        return map;
    }

    private static RateLimitPolicy toPolicy(JsonNode node) {
        String code = text(node, "policyCode");
        RateLimitAlgorithm algorithm = RateLimitAlgorithm.valueOf(text(node, "algorithm"));
        long limit = node.hasNonNull("limit") ? node.get("limit").asLong() : node.path("limitCount").asLong();
        long burst = node.hasNonNull("burst") ? node.get("burst").asLong() : limit;
        StoreFailurePolicy failure = StoreFailurePolicy.valueOf(text(node, "storeFailurePolicy"));
        return RateLimitPolicy.builder()
                .policyCode(code)
                .algorithm(algorithm)
                .limit(limit)
                .period(Duration.ofMillis(node.path("periodMs").asLong()))
                .burst(burst)
                .storeFailurePolicy(failure)
                .enabled(node.path("enabled").asBoolean(true))
                .version(node.path("version").asLong(0L))
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }
}
