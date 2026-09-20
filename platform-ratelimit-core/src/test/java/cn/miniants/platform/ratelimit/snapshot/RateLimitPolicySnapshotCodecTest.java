package cn.miniants.platform.ratelimit.snapshot;

import cn.miniants.platform.core.json.Jsons;
import cn.miniants.platform.ratelimit.RateLimitAlgorithm;
import cn.miniants.platform.ratelimit.RateLimitPolicy;
import cn.miniants.platform.ratelimit.StoreFailurePolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitPolicySnapshotCodecTest {

    @Test
    void roundTripAndIgnoreAdminFields() {
        RateLimitPolicySnapshotCodec codec = new RateLimitPolicySnapshotCodec(Jsons.mapper());
        RateLimitPolicy policy = RateLimitPolicy.builder()
                .policyCode("auth.login")
                .algorithm(RateLimitAlgorithm.GCRA)
                .limit(20)
                .period(Duration.ofMinutes(1))
                .burst(40)
                .storeFailurePolicy(StoreFailurePolicy.DENY)
                .enabled(true)
                .version(3)
                .build();
        String json = codec.encode(12L, List.of(policy));
        assertTrue(json.contains("\"revision\":12"));
        RateLimitPolicySnapshot decoded = codec.decode(
                "{\"revision\":12,\"policies\":[{\"id\":1,\"policyCode\":\"auth.login\","
                        + "\"algorithm\":\"GCRA\",\"limitCount\":20,\"periodMs\":60000,"
                        + "\"burst\":40,\"storeFailurePolicy\":\"DENY\",\"enabled\":true,\"version\":3}]}");
        assertEquals(12L, decoded.revision());
        assertEquals(20L, decoded.policies().getFirst().limit());
        assertEquals("auth.login", decoded.policies().getFirst().policyCode());
    }
}
