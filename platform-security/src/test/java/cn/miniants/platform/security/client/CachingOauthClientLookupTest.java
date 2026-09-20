package cn.miniants.platform.security.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingOauthClientLookupTest {

    @Test
    void cachesEnabledDescriptor() {
        OauthClientDescriptor enabled = descriptor("web", true);
        AtomicInteger hits = new AtomicInteger();
        OauthClientLookup jdbc = clientId -> {
            hits.incrementAndGet();
            return Optional.of(enabled);
        };
        InMemoryCache cache = new InMemoryCache();
        CachingOauthClientLookup lookup = new CachingOauthClientLookup(jdbc, cache);

        assertThat(lookup.findByClientId("web")).contains(enabled);
        assertThat(lookup.findByClientId("web")).contains(enabled);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(cache.get("web")).contains(enabled);
    }

    @Test
    void doesNotCacheDisabled() {
        OauthClientDescriptor disabled = descriptor("web", false);
        OauthClientLookup jdbc = clientId -> Optional.of(disabled);
        InMemoryCache cache = new InMemoryCache();
        CachingOauthClientLookup lookup = new CachingOauthClientLookup(jdbc, cache);

        assertThat(lookup.findByClientId("web")).isEmpty();
        assertThat(cache.get("web")).isEmpty();
    }

    @Test
    void builtinFallbackAfterJdbcMiss() {
        OauthClientDescriptor builtin = descriptor("inner", true);
        OauthClientLookup jdbc = clientId -> Optional.empty();
        BuiltinFallbackOauthClientLookup lookup = new BuiltinFallbackOauthClientLookup(
                jdbc, List.of(id -> Optional.of(builtin)));
        assertThat(lookup.findByClientId("inner")).contains(builtin);
    }

    private static OauthClientDescriptor descriptor(String clientId, boolean enabled) {
        return new OauthClientDescriptor(
                clientId, clientId, "{bcrypt}x", clientId,
                List.of("client_credentials"), List.of("task"), 3600, 86400, enabled);
    }

    private static final class InMemoryCache implements OauthClientCache {
        private OauthClientDescriptor value;

        @Override
        public Optional<OauthClientDescriptor> get(String clientId) {
            return Optional.ofNullable(value);
        }

        @Override
        public void put(String clientId, OauthClientDescriptor descriptor) {
            this.value = descriptor;
        }

        @Override
        public void evict(String clientId) {
            this.value = null;
        }
    }
}
