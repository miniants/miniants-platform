package cn.miniants.platform.security.client;

import java.util.List;
import java.util.Optional;

/**
 * 先 JDBC，再 {@link OauthClientBuiltinSource}。不负责缓存。
 */
public final class BuiltinFallbackOauthClientLookup implements OauthClientLookup {

    private final OauthClientLookup jdbc;
    private final List<OauthClientBuiltinSource> builtins;

    public BuiltinFallbackOauthClientLookup(OauthClientLookup jdbc, List<OauthClientBuiltinSource> builtins) {
        this.jdbc = jdbc;
        this.builtins = builtins == null ? List.of() : List.copyOf(builtins);
    }

    @Override
    public Optional<OauthClientDescriptor> findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        Optional<OauthClientDescriptor> fromDb = jdbc.findByClientId(clientId.trim());
        if (fromDb.isPresent()) {
            return fromDb.filter(OauthClientDescriptor::enabled);
        }
        String id = clientId.trim();
        for (OauthClientBuiltinSource source : builtins) {
            Optional<OauthClientDescriptor> builtin = source.findByClientId(id);
            if (builtin.isPresent()) {
                return builtin.filter(OauthClientDescriptor::enabled);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<OauthClientDescriptor> findById(String id) {
        return jdbc.findById(id).filter(OauthClientDescriptor::enabled);
    }
}
