package cn.miniants.platform.security.person;

import cn.miniants.platform.security.account.UserAccount;

import java.util.List;
import java.util.Optional;

/**
 * 按自然人列账号；选身份时校验 designated 必须是同一 person 下启用账号。
 */
public interface PrincipalDirectory {

    List<PrincipalSummary> listByPersonId(Long personId);

    /**
     * 解析指定账号：必须存在、启用、且 {@code person_id} 与入参一致。
     */
    Optional<UserAccount> resolveDesignated(Long personId, String designatedUsername);
}
