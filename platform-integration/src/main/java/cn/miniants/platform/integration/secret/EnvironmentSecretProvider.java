package cn.miniants.platform.integration.secret;

import java.util.Optional;

public class EnvironmentSecretProvider implements SecretProvider {

    private final SecretProperties properties;

    public EnvironmentSecretProvider(SecretProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String value = properties.getValues().get(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
