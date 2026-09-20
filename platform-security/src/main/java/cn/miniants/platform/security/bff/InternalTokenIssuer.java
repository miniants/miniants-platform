package cn.miniants.platform.security.bff;

import java.util.Map;

/**
 * 进程内换票，不校验 client_secret。供第一方门面使用。
 */
public interface InternalTokenIssuer {

    Map<String, Object> issuePassword(String clientId, String username, String password);

    Map<String, Object> issueRefresh(String clientId, String refreshToken);
}
