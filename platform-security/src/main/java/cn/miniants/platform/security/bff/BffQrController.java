package cn.miniants.platform.security.bff;

import cn.miniants.platform.core.error.PlatformException;
import cn.miniants.platform.security.PublicAccess;
import cn.miniants.platform.security.qr.QrCodeRenderer;
import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStore;
import cn.miniants.platform.security.sas.PlatformGrantTypes;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@PublicAccess
@RequestMapping("/auth/open/{channel}/qrcode")
public class BffQrController {

    private final PlatformBffProperties properties;
    private final QrLoginStore qrLoginStore;
    private final QrCodeRenderer qrCodeRenderer;
    private final QrTokenFacade qrTokenFacade;
    private final QrLoginSseHub qrLoginSseHub;
    private final RegisteredClientRepository registeredClientRepository;

    public BffQrController(
            PlatformBffProperties properties,
            QrLoginStore qrLoginStore,
            QrCodeRenderer qrCodeRenderer,
            QrTokenFacade qrTokenFacade,
            QrLoginSseHub qrLoginSseHub,
            RegisteredClientRepository registeredClientRepository) {
        this.properties = properties;
        this.qrLoginStore = qrLoginStore;
        this.qrCodeRenderer = qrCodeRenderer;
        this.qrTokenFacade = qrTokenFacade;
        this.qrLoginSseHub = qrLoginSseHub;
        this.registeredClientRepository = registeredClientRepository;
    }

    @GetMapping
    public Map<String, Object> create(@PathVariable("channel") String channel) {
        String clientId = requireQrClient(channel);
        QrLoginSession session = qrLoginStore.create(clientId);
        byte[] bytes = qrCodeRenderer.render(session.scene());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", session.scene());
        if (bytes != null && bytes.length > 0) {
            body.put("qrcodeImg", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes));
        }
        return body;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable("channel") String channel,
            @RequestParam(value = "scene", required = false) String scene,
            HttpServletResponse response) {
        requireQrClient(channel);
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        return qrLoginSseHub.subscribe(channel, scene);
    }

    /**
     * 自助机与内核约定：只接受 POST。
     */
    @PostMapping("/auth-query")
    public Map<String, Object> authQuery(
            @PathVariable("channel") String channel,
            @RequestParam("scene") String scene) {
        requireQrClient(channel);
        if (scene == null || scene.isBlank()) {
            throw new PlatformException("二维码已失效");
        }
        return qrTokenFacade.queryAndIssue(scene.trim());
    }

    private String requireQrClient(String channel) {
        String clientId = properties.getQr().requireClientId(channel);
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null || !client.getAuthorizationGrantTypes().contains(PlatformGrantTypes.QR)) {
            throw new PlatformException("客户端未启用扫码登录");
        }
        return clientId;
    }
}
