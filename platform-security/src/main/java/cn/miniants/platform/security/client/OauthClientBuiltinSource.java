package cn.miniants.platform.security.client;

import java.util.Optional;

/**
 * 产品内置 OAuth 客户端（库无行时的兜底）。由业务仓注册；内核 JDBC 查无后再问这里。
 */
@FunctionalInterface
public interface OauthClientBuiltinSource {

    Optional<OauthClientDescriptor> findByClientId(String clientId);
}
