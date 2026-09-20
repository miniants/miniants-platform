package cn.miniants.platform.ratelimit;

import cn.miniants.platform.ratelimit.config.RateLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitPropertiesBindingTest {

    @Test
    void bindsDefaultsAndPolicies() {
        Map<String, String> map = new HashMap<>();
        map.put("platform.ratelimit.enabled", "true");
        map.put("platform.ratelimit.backend", "local");
        map.put("platform.ratelimit.key-prefix", "app:rl:");
        map.put("platform.ratelimit.stale-snapshot-max-age", "10m");
        map.put("platform.ratelimit.policies[auth.login].algorithm", "GCRA");
        map.put("platform.ratelimit.policies[auth.login].limit", "20");
        map.put("platform.ratelimit.policies[auth.login].period", "1m");
        map.put("platform.ratelimit.policies[auth.login].burst", "30");
        map.put("platform.ratelimit.policies[auth.login].store-failure-policy", "ALLOW");
        map.put("platform.ratelimit.policies[auth.login].enabled", "true");
        map.put("platform.ratelimit.policies[auth.login].version", "3");
        map.put("platform.ratelimit.trusted-proxies[0]", "10.0.0.0/8");
        map.put("platform.ratelimit.trusted-proxies[1]", "192.168.0.0/16");

        RateLimitProperties props = new Binder(new MapConfigurationPropertySource(map))
                .bind("platform.ratelimit", Bindable.of(RateLimitProperties.class))
                .get();

        assertTrue(props.isEnabled());
        assertEquals(RateLimitBackend.LOCAL, props.backendType());
        assertEquals("app:rl:", props.getKeyPrefix());
        assertEquals(Duration.ofMinutes(10), props.getStaleSnapshotMaxAge());
        assertEquals(2, props.getTrustedProxies().size());
        assertEquals("10.0.0.0/8", props.getTrustedProxies().get(0));

        RateLimitProperties.PolicyDefinition def = props.getPolicies().get("auth.login");
        assertEquals(RateLimitAlgorithm.GCRA, def.getAlgorithm());
        assertEquals(20, def.getLimit());
        assertEquals("1m", def.getPeriod());
        assertEquals(30L, def.getBurst());
        assertEquals(StoreFailurePolicy.ALLOW, def.getStoreFailurePolicy());
        assertEquals(3, def.getVersion());

        RateLimitPolicy policy = def.toPolicy("auth.login");
        assertEquals("auth.login", policy.policyCode());
        assertEquals(Duration.ofMinutes(1), policy.period());
        assertEquals(30, policy.burst());
        assertEquals(3, policy.version());
    }

    @Test
    void unknownBackendRejected() {
        RateLimitProperties props = new RateLimitProperties();
        props.setBackend("memory");
        assertThrows(IllegalArgumentException.class, props::backendType);
    }

    @Test
    void burstDefaultsToLimitWhenBuildingPolicy() {
        RateLimitProperties.PolicyDefinition def = new RateLimitProperties.PolicyDefinition();
        def.setLimit(7);
        def.setPeriod("30s");
        def.setAlgorithm(RateLimitAlgorithm.SLIDING_WINDOW);
        RateLimitPolicy policy = def.toPolicy("sms.send");
        assertEquals(7, policy.burst());
        assertEquals(Duration.ofSeconds(30), policy.period());
        assertFalse(policy.period().isZero());
    }
}
