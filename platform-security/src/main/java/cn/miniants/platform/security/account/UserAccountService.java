package cn.miniants.platform.security.account;

import java.util.List;
import java.util.Optional;

public interface UserAccountService {

    Optional<UserAccount> findByUsername(String username);

    default Optional<UserAccount> findById(Long id) {
        return Optional.empty();
    }

    default void updatePasswordHash(Long id, String passwordHash) {
        throw new UnsupportedOperationException("当前实现不支持修改密码");
    }

    default List<UserAccount> findEnabledByPersonId(Long personId) {
        return List.of();
    }

    default void updatePersonId(Long userId, Long personId) {
        throw new UnsupportedOperationException("当前实现不支持更新 person_id");
    }
}
