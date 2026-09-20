package cn.miniants.platform.security.account;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserAccountService implements UserAccountService {

    private final Map<String, UserAccount> users = new ConcurrentHashMap<>();

    public InMemoryUserAccountService add(UserAccount account) {
        users.put(account.username(), account);
        return this;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }

    @Override
    public Optional<UserAccount> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return users.values().stream().filter(account -> id.equals(account.id())).findFirst();
    }

    @Override
    public void updatePasswordHash(Long id, String passwordHash) {
        UserAccount existing = findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserAccount updated = new UserAccount(
                existing.id(),
                existing.username(),
                passwordHash,
                existing.displayName(),
                existing.enabled(),
                existing.sysAdmin(),
                existing.roleIds(),
                existing.personId());
        users.put(existing.username(), updated);
    }

    @Override
    public List<UserAccount> findEnabledByPersonId(Long personId) {
        if (personId == null) {
            return List.of();
        }
        return users.values().stream()
                .filter(account -> account.enabled() && Objects.equals(personId, account.personId()))
                .toList();
    }

    @Override
    public void updatePersonId(Long userId, Long personId) {
        UserAccount existing = findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserAccount updated = new UserAccount(
                existing.id(),
                existing.username(),
                existing.passwordHash(),
                existing.displayName(),
                existing.enabled(),
                existing.sysAdmin(),
                existing.roleIds(),
                personId);
        users.put(existing.username(), updated);
    }
}
