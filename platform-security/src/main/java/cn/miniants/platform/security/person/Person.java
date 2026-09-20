package cn.miniants.platform.security.person;

import java.time.Instant;

/**
 * 自然人。不含学工号；证件号只以摘要 / 可选密文落库，明文不得进 JWT。
 */
public record Person(
        Long id,
        String realName,
        String idDocType,
        String idLookupDigest,
        Instant verifiedAt
) {
}
