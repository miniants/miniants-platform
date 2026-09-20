package cn.miniants.platform.security.client;

import java.util.Optional;

/**
 * 先读 {@link OauthClientCache}，未命中再委托；仅缓存 enabled 快照。
 */
public final class CachingOauthClientLookup implements OauthClientLookup {

    private final OauthClientLookup delegate;
    private final OauthClientCache cache;

    public CachingOauthClientLookup(OauthClientLookup delegate, OauthClientCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<OauthClientDescriptor> findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        String id = clientId.trim();
        Optional<OauthClientDescriptor> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached.filter(OauthClientDescriptor::enabled);
        }
        Optional<OauthClientDescriptor> loaded = delegate.findByClientId(id)
                .filter(OauthClientDescriptor::enabled);
        loaded.ifPresent(descriptor -> cache.put(id, descriptor));
        return loaded;
    }

    @Override
    public Optional<OauthClientDescriptor> findById(String id) {
        return delegate.findById(id);
    }
}
