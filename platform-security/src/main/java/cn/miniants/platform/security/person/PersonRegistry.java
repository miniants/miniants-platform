package cn.miniants.platform.security.person;

import java.util.Optional;

/**
 * 自然人注册表：按证号摘要验人、建人、合并。明文证件号不得写入实现日志。
 */
public interface PersonRegistry {

    Optional<Person> findById(Long id);

    Optional<Person> findByLookupDigest(String digest);

    /**
     * 按摘要取或建人；已存在则更新实名与验真时间。{@code optionalCipher} 非空时写入密文列。
     * 调用方须先用 {@link cn.miniants.platform.security.crypto.IdCredentialHasher} 生成摘要，
     * 不得把明文证件号传入本方法以外的持久化路径。
     */
    Person verifyOrCreate(String realName, String idDocType, String lookupDigest, String optionalCipher);

    /**
     * 无证号时建未验真人；{@code id_lookup_digest} 为空，下次验真再补。
     */
    Person createUnverified(String realName);

    /**
     * 将 duplicate 的账号与外部身份迁到 canonical 后软删 duplicate。
     * 禁止留下孤儿 {@code sys_user.person_id} / {@code sys_external_identity.person_id}。
     */
    Person merge(Long canonicalId, Long duplicateId);
}
