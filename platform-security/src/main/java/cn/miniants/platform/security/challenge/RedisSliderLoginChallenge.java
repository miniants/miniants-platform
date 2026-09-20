package cn.miniants.platform.security.challenge;

import cn.miniants.platform.core.error.PlatformException;

import java.util.Map;

/**
 * 默认 {@link LoginChallenge}：失败计数后强制滑块。
 */
public class RedisSliderLoginChallenge implements LoginChallenge {

    private final RedisSliderCaptchaService captchaService;

    public RedisSliderLoginChallenge(RedisSliderCaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @Override
    public boolean requiresChallenge(String clientKey) {
        return captchaService.captchaRequired(clientKey);
    }

    @Override
    public void assertPassed(String clientKey, Map<String, String> params) {
        String captchaId = params == null ? null : params.get("captchaId");
        Integer offsetX = parseOffset(params == null ? null : params.get("offsetX"));
        try {
            // BFF 已判断 requiresChallenge；此处强制校验滑块
            captchaService.assertSliderIfRequired(clientKey, captchaId, offsetX);
            if (captchaService.captchaRequired(clientKey)
                    && (captchaId == null || captchaId.isBlank() || offsetX == null)) {
                throw new PlatformException(RedisSliderCaptchaService.MSG_NEED_SLIDER);
            }
        } catch (PlatformException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PlatformException(
                    ex.getMessage() == null ? RedisSliderCaptchaService.MSG_BAD_SLIDER : ex.getMessage());
        }
    }

    @Override
    public void recordFailure(String clientKey) {
        captchaService.markLoginFailed(clientKey);
    }

    @Override
    public void clear(String clientKey) {
        captchaService.clearLoginFailed(clientKey);
    }

    private static Integer parseOffset(String raw) {
        if (raw == null || raw.isBlank() || "undefined".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
