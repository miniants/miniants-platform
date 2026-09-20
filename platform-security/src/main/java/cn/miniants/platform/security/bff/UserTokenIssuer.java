package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.account.UserAccount;

import java.util.Map;

/**
 * 进程内用户票签发：密码 / 刷新 / 已解析账号。不校验 client_secret。
 */
public interface UserTokenIssuer extends InternalTokenIssuer {

    /**
     * 对已解析且启用的账号出票（外部身份、扫码、学校协议适配）。
     */
    Map<String, Object> issueForAccount(String clientId, UserAccount account);

    /**
     * 带 grant 出票并记登录审计。{@code grant} 为空则只出票不记（由调用方自行审计）。
     */
    default Map<String, Object> issueForAccount(String clientId, UserAccount account, String grant) {
        return issueForAccount(clientId, account);
    }

    /**
     * 密码出票；{@code designatedUsername} 非空时须为同一 person 下启用账号。
     */
    Map<String, Object> issuePassword(
            String clientId, String username, String password, String designatedUsername);
}
