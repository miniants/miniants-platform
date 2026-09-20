package cn.miniants.platform.security.client;

import java.util.Optional;

public interface OauthClientLookup {

    Optional<OauthClientDescriptor> findByClientId(String clientId);

    default Optional<OauthClientDescriptor> findById(String id) {
        return Optional.empty();
    }
}
