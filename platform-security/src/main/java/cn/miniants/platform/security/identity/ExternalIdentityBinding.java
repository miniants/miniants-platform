package cn.miniants.platform.security.identity;

import java.util.Optional;

/**
 * {@code (provider, subject) ↔ person}；UNIQUE(provider, subject)。
 */
public interface ExternalIdentityBinding {

    Optional<ExternalIdentity> find(String provider, String subject);

    /**
     * 按人反查某 provider 下的一条外部身份（如小程序 openid）。多人多绑时取最早一条。
     */
    Optional<ExternalIdentity> findByPerson(String provider, Long personId);

    /**
     * 绑定或更新：已存在同 (provider, subject) 则改 person_id。
     */
    ExternalIdentity bind(String provider, String subject, Long personId);

    /**
     * 合并自然人时把全部外部身份从 from 迁到 to。
     */
    void reassignPerson(Long fromPersonId, Long toPersonId);

    /**
     * 软删该人在某 provider 下的全部外部身份。无行返回 {@code false}。
     */
    boolean unbindByPerson(String provider, Long personId);
}
