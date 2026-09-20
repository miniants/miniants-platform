package cn.miniants.platform.security.identity;

import java.util.Map;

/**
 * 将外部凭据解析为 {@code (provider, subject)}。无对应 Bean 则该 grant 不可用。
 */
public interface ExternalIdentityProvider {

    /** 稳定 provider 字符串，与 {@link ExternalIdentity#provider()} / 绑定表一致。 */
    String id();

    /**
     * @param credentials 渠道相关键值（如 code / openid）；实现不得把敏感原文写入业务日志
     * @return 解析结果；{@code personId} 可为 null（尚未绑定）
     */
    ExternalIdentity resolve(Map<String, String> credentials);
}
