package cn.miniants.platform.security.bff;

import cn.miniants.platform.security.qr.QrLoginStatus;
import cn.miniants.platform.security.qr.QrLoginSession;
import cn.miniants.platform.security.qr.QrLoginStore;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫码 SSE：单进程共享 tick 读 Redis，不按连接阻塞线程。
 */
public class QrLoginSseHub {

    static final long EMITTER_TIMEOUT_MS = 5L * 60L * 1000L;
    static final long TICK_INTERVAL_MS = 200L;
    static final long HEARTBEAT_INTERVAL_MS = 15L * 1000L;

    private final QrLoginStore qrLoginStore;
    private final QrTokenFacade qrTokenFacade;
    private final org.springframework.scheduling.TaskScheduler taskScheduler;

    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final AtomicBoolean tickScheduled = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> tickFuture;

    public QrLoginSseHub(
            QrLoginStore qrLoginStore,
            QrTokenFacade qrTokenFacade,
            org.springframework.scheduling.TaskScheduler taskScheduler) {
        this.qrLoginStore = qrLoginStore;
        this.qrTokenFacade = qrTokenFacade;
        this.taskScheduler = taskScheduler;
    }

    public SseEmitter subscribe(String channel, String scene) {
        String trimmedScene = scene == null ? "" : scene.trim();
        if (trimmedScene.isBlank()) {
            return expireImmediately(channel, "", "二维码已失效");
        }
        QrLoginSession session = qrLoginStore.find(trimmedScene).orElse(null);
        if (session == null) {
            return expireImmediately(channel, trimmedScene, "二维码已失效");
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        String id = UUID.randomUUID().toString();
        Subscription subscription = new Subscription(id, emitter, channel, trimmedScene, session.status());
        subscriptions.put(id, subscription);

        emitter.onCompletion(() -> subscriptions.remove(id));
        emitter.onTimeout(() -> subscriptions.remove(id));
        emitter.onError(ex -> subscriptions.remove(id));

        if (session.status() == QrLoginStatus.SUCCESS) {
            Map<String, Object> body = qrTokenFacade.queryAndIssue(trimmedScene);
            sendJson(subscription, body);
            complete(subscription);
            return emitter;
        }
        sendStatus(subscription, session.status(), null);
        ensureTickScheduled();
        return emitter;
    }

    private void ensureTickScheduled() {
        if (!tickScheduled.compareAndSet(false, true)) {
            return;
        }
        tickFuture = taskScheduler.scheduleAtFixedRate(this::tick, Duration.ofMillis(TICK_INTERVAL_MS));
    }

    private void stopTickIfIdle() {
        if (!subscriptions.isEmpty()) {
            return;
        }
        ScheduledFuture<?> future = tickFuture;
        if (future != null) {
            future.cancel(false);
            tickFuture = null;
        }
        tickScheduled.set(false);
    }

    void tick() {
        if (subscriptions.isEmpty()) {
            stopTickIfIdle();
            return;
        }
        long now = System.currentTimeMillis();
        for (Subscription subscription : subscriptions.values()) {
            try {
                tickOne(subscription, now);
            } catch (RuntimeException ex) {
                completeWithError(subscription, ex.getMessage() == null ? "扫码状态查询失败" : ex.getMessage());
            }
        }
        if (subscriptions.isEmpty()) {
            stopTickIfIdle();
        }
    }

    private void tickOne(Subscription subscription, long now) {
        if (subscription.completed) {
            subscriptions.remove(subscription.id);
            return;
        }
        QrLoginSession session = qrLoginStore.find(subscription.scene).orElse(null);
        if (session == null) {
            completeWithError(subscription, "二维码已失效");
            return;
        }
        QrLoginStatus status = session.status();
        if (status != subscription.lastStatus) {
            if (status == QrLoginStatus.SUCCESS) {
                Map<String, Object> body = qrTokenFacade.queryAndIssue(subscription.scene);
                sendJson(subscription, body);
                complete(subscription);
                return;
            }
            sendStatus(subscription, status, null);
            subscription.lastStatus = status;
        }
        if (now - subscription.lastWriteMs >= HEARTBEAT_INTERVAL_MS) {
            sendHeartbeat(subscription);
        }
    }

    private void sendStatus(Subscription subscription, QrLoginStatus status, Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", subscription.scene);
        body.put("status", status.name());
        if (extra != null) {
            body.putAll(extra);
        }
        sendJson(subscription, body);
        subscription.lastStatus = status;
    }

    private void sendJson(Subscription subscription, Map<String, Object> body) {
        try {
            subscription.emitter.send(SseEmitter.event()
                    .name("message")
                    .data(body, MediaType.APPLICATION_JSON));
            subscription.lastWriteMs = System.currentTimeMillis();
        } catch (IOException ex) {
            disconnectClient(subscription);
        }
    }

    private void sendHeartbeat(Subscription subscription) {
        try {
            subscription.emitter.send(SseEmitter.event().comment("keepalive"));
            subscription.lastWriteMs = System.currentTimeMillis();
        } catch (IOException ex) {
            disconnectClient(subscription);
        }
    }

    /** 客户端已断开：移除订阅并静默 complete，不向 Advice 抛错。 */
    private void disconnectClient(Subscription subscription) {
        if (subscription.completed) {
            return;
        }
        subscription.completed = true;
        subscriptions.remove(subscription.id);
        try {
            subscription.emitter.complete();
        } catch (RuntimeException ignored) {
            // 连接已关闭
        }
    }

    /** 订阅当下已失效：建 emitter、推 INVALID、立刻 complete，不进共享 tick。 */
    private SseEmitter expireImmediately(String channel, String scene, String message) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        completeWithError(new Subscription(UUID.randomUUID().toString(), emitter, channel, scene, null), message);
        return emitter;
    }

    private void completeWithError(Subscription subscription, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scene", subscription.scene);
        body.put("status", "INVALID");
        body.put("message", message);
        sendJson(subscription, body);
        complete(subscription);
    }

    private void complete(Subscription subscription) {
        if (subscription.completed) {
            return;
        }
        subscription.completed = true;
        subscriptions.remove(subscription.id);
        try {
            subscription.emitter.complete();
        } catch (RuntimeException ignored) {
            // 连接已关闭
        }
    }

    static final class Subscription {
        private final String id;
        private final SseEmitter emitter;
        private final String channel;
        private final String scene;
        private QrLoginStatus lastStatus;
        private long lastWriteMs = System.currentTimeMillis();
        private volatile boolean completed;

        Subscription(String id, SseEmitter emitter, String channel, String scene, QrLoginStatus lastStatus) {
            this.id = id;
            this.emitter = emitter;
            this.channel = channel;
            this.scene = scene;
            this.lastStatus = lastStatus;
        }
    }
}
