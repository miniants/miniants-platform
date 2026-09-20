package cn.miniants.platform.security.client;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOauthClientLookup implements OauthClientLookup {

    private final Map<String, OauthClientDescriptor> byClientId = new ConcurrentHashMap<>();
    private final Map<String, OauthClientDescriptor> byId = new ConcurrentHashMap<>();

    public InMemoryOauthClientLookup add(OauthClientDescriptor client) {
        byClientId.put(client.clientId(), client);
        byId.put(client.id(), client);
        return this;
    }

    @Override
    public Optional<OauthClientDescriptor> findByClientId(String clientId) {
        return Optional.ofNullable(byClientId.get(clientId));
    }

    @Override
    public Optional<OauthClientDescriptor> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
