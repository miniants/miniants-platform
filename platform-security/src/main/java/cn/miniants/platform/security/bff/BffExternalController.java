package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.PublicAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PublicAccess
@RequestMapping("/auth/open")
@ConditionalOnProperty(prefix = "platform.security.bff.external", name = "enabled", havingValue = "true")
public class BffExternalController {

    private final BffExternalLoginService loginService;

    public BffExternalController(BffExternalLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/{audience}/external")
    public Map<String, Object> external(
            @PathVariable("audience") String audience,
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        return loginService.login(audience, params, request);
    }

    @PostMapping("/{audience}/refresh")
    public Map<String, Object> refresh(
            @PathVariable("audience") String audience,
            @RequestParam("refresh_token") String refreshToken,
            HttpServletRequest request) {
        return loginService.refresh(audience, refreshToken, request);
    }
}
