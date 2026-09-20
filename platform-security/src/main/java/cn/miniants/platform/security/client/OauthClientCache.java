package cn.miniants.platform.security.client;

import java.util.Optional;

/**
 * OAuth 客户端发票读路径缓存。实现可无（无 Redis 时直查库）。
 */
public interface OauthClientCache {

    Optional<OauthClientDescriptor> get(String clientId);

    void put(String clientId, OauthClientDescriptor descriptor);

    void evict(String clientId);
}
