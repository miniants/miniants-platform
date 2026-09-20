package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStatus;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QrLoginSseHubTest {

    private QrLoginStore store;
    private QrTokenFacade facade;
    private QrLoginSseHub hub;

    @BeforeEach
    void setUp() {
        store = mock(QrLoginStore.class);
        facade = mock(QrTokenFacade.class);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        hub = new QrLoginSseHub(store, facade, scheduler);
    }

    @Test
    void subscribeReturnsEmitterForInitSession() {
        when(store.find("scene1")).thenReturn(Optional.of(
                new QrLoginSession("scene1", QrLoginStatus.INIT, "JWY_WEB", null, null, null)));

        SseEmitter emitter = hub.subscribe("web", "scene1");

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(QrLoginSseHub.EMITTER_TIMEOUT_MS);
    }

    @Test
    void subscribeIssuesTokenWhenAlreadySuccess() {
        when(store.find("scene2")).thenReturn(Optional.of(
                new QrLoginSession("scene2", QrLoginStatus.SUCCESS, "JWY_WEB", "WX", "oid", null)));
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("scene", "scene2");
        token.put("status", "SUCCESS");
        token.put("jwtData", Map.of("access_token", "t"));
        when(facade.queryAndIssue("scene2")).thenReturn(token);

        SseEmitter emitter = hub.subscribe("web", "scene2");

        assertThat(emitter).isNotNull();
    }

    @Test
    void tickIssuesTokenOnSuccess() {
        when(store.find("scene3")).thenReturn(Optional.of(
                new QrLoginSession("scene3", QrLoginStatus.SCANNED, "JWY_WEB", "WX", "oid", null)));
        hub.subscribe("web", "scene3");

        when(store.find("scene3")).thenReturn(Optional.of(
                new QrLoginSession("scene3", QrLoginStatus.SUCCESS, "JWY_WEB", "WX", "oid", null)));
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("scene", "scene3");
        token.put("status", "SUCCESS");
        when(facade.queryAndIssue("scene3")).thenReturn(token);

        hub.tick();
    }

    @Test
    void subscribeMissingSceneReturnsEmitterWithoutThrowing() {
        when(store.find("gone")).thenReturn(Optional.empty());

        SseEmitter emitter = hub.subscribe("web", "gone");

        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(QrLoginSseHub.EMITTER_TIMEOUT_MS);
        hub.tick();
        verify(store, times(1)).find("gone");
    }

    @Test
    void subscribeBlankSceneDoesNotLookupStore() {
        assertThatCode(() -> hub.subscribe("web", "  ")).doesNotThrowAnyException();
        assertThat(hub.subscribe("web", null)).isNotNull();
        verifyNoInteractions(store);
    }
}
