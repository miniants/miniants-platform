package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.PublicAccess;
import cn.miniants.platform.security.challenge.RedisSliderCaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 密码登录配套：是否要挑战、滑块出题。URI 与现网对齐。
 */
@RestController
@PublicAccess
@RequestMapping("/auth/open/web")
public class BffChallengeController {

    private final RedisSliderCaptchaService captchaService;

    public BffChallengeController(RedisSliderCaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/slider-captcha")
    public Map<String, Object> sliderCaptcha() {
        return captchaService.createSlider();
    }

    @GetMapping("/login-challenge")
    public Map<String, Object> loginChallenge(HttpServletRequest request) {
        String ip = RedisSliderCaptchaService.clientIp(request);
        return Map.of("captchaRequired", captchaService.captchaRequired(ip));
    }
}
