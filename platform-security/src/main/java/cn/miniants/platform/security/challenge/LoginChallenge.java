package cn.miniants.platform.security.challenge;

import java.util.Map;

/**
 * 登录二次校验（滑块 / 验证码等）。关掉 {@code bff.challenge} 则密码口不做挑战。
 */
public interface LoginChallenge {

    boolean requiresChallenge(String clientKey);

    /**
     * 未通过时抛业务异常（中文消息）。
     */
    void assertPassed(String clientKey, Map<String, String> params);

    void recordFailure(String clientKey);

    void clear(String clientKey);
}
