package cn.miniants.platform.security.challenge;

import cn.miniants.platform.core.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 失败计数 + AWT 滑块题。供 BFF 密码口与挑战门面共用。
 */
public class RedisSliderCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RedisSliderCaptchaService.class);

    public static final String MSG_NEED_SLIDER = "需要完成滑块验证";
    public static final String MSG_BAD_SLIDER = "滑块验证失败，请重试";

    private static final Duration FAIL_TTL = Duration.ofMinutes(15);
    private static final Duration SLIDER_TTL = Duration.ofMinutes(3);
    private static final int FAIL_THRESHOLD = 1;
    private static final int TOLERANCE_PX = 5;

    private static final int BG_W = 300;
    private static final int BG_H = 150;
    private static final int PIECE = 42;

    private final StringRedisTemplate redis;
    private final String failKeyPrefix;
    private final String sliderKeyPrefix;

    public RedisSliderCaptchaService(StringRedisTemplate redis, String redisKeyPrefix) {
        this.redis = redis;
        String base = normalizePrefix(redisKeyPrefix);
        this.failKeyPrefix = base + "fail:";
        this.sliderKeyPrefix = base + "slider:";
    }

    public boolean captchaRequired(String clientIp) {
        try {
            return failCount(clientIp) >= FAIL_THRESHOLD;
        } catch (RuntimeException ex) {
            log.warn("登录失败计数读取失败，本次不强制滑块: {}", ex.getMessage());
            return false;
        }
    }

    public Map<String, Object> createSlider() {
        int maxX = BG_W - PIECE - 12;
        int offsetX = ThreadLocalRandom.current().nextInt(PIECE + 8, Math.max(PIECE + 9, maxX));
        int offsetY = ThreadLocalRandom.current().nextInt(12, BG_H - PIECE - 12);

        BufferedImage background = new BufferedImage(BG_W, BG_H, BufferedImage.TYPE_INT_RGB);
        BufferedImage slider = new BufferedImage(PIECE, PIECE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg = background.createGraphics();
        Graphics2D sg = slider.createGraphics();
        try {
            paintBackground(bg);
            cutPiece(background, slider, offsetX, offsetY);
            dimHole(bg, offsetX, offsetY);
        } finally {
            bg.dispose();
            sg.dispose();
        }

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(sliderKeyPrefix + captchaId, Integer.toString(offsetX), SLIDER_TTL);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("captchaId", captchaId);
        result.put("backgroundImage", "data:image/png;base64," + toPngBase64(background));
        result.put("sliderImage", "data:image/png;base64," + toPngBase64(slider));
        result.put("y", offsetY);
        result.put("bgWidth", BG_W);
        result.put("bgHeight", BG_H);
        result.put("pieceSize", PIECE);
        return result;
    }

    public void assertSliderIfRequired(String clientIp, String captchaId, Integer offsetX) {
        if (!captchaRequired(clientIp)) {
            return;
        }
        if (captchaId == null || captchaId.isBlank() || offsetX == null) {
            throw new PlatformException(MSG_NEED_SLIDER);
        }
        String key = sliderKeyPrefix + captchaId.trim();
        String raw = redis.opsForValue().get(key);
        redis.delete(key);
        if (raw == null || raw.isBlank()) {
            throw new PlatformException(MSG_BAD_SLIDER);
        }
        int expect;
        try {
            expect = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new PlatformException(MSG_BAD_SLIDER);
        }
        if (Math.abs(offsetX - expect) > TOLERANCE_PX) {
            throw new PlatformException(MSG_BAD_SLIDER);
        }
    }

    public void markLoginFailed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        String key = failKeyPrefix + clientIp.trim();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, FAIL_TTL);
        }
    }

    public void clearLoginFailed(String clientIp) {
        if (clientIp != null && !clientIp.isBlank()) {
            redis.delete(failKeyPrefix + clientIp.trim());
        }
    }

    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private long failCount(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return 0;
        }
        String raw = redis.opsForValue().get(failKeyPrefix + clientIp.trim());
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalizePrefix(String redisKeyPrefix) {
        if (redisKeyPrefix == null || redisKeyPrefix.isBlank()) {
            return "platform:auth:web-";
        }
        return redisKeyPrefix.endsWith("-") || redisKeyPrefix.endsWith(":")
                ? redisKeyPrefix
                : redisKeyPrefix + "-";
    }

    private static void paintBackground(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint paint = new GradientPaint(0, 0, new Color(56, 120, 198), BG_W, BG_H, new Color(32, 74, 128));
        g.setPaint(paint);
        g.fillRect(0, 0, BG_W, BG_H);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 28; i++) {
            g.setColor(new Color(255, 255, 255, 30 + random.nextInt(50)));
            int x = random.nextInt(BG_W);
            int y = random.nextInt(BG_H);
            int w = 8 + random.nextInt(40);
            g.fillOval(x, y, w, w / 2 + 4);
        }
        g.setColor(new Color(255, 255, 255, 40));
        for (int i = 0; i < 6; i++) {
            g.drawLine(random.nextInt(BG_W), random.nextInt(BG_H), random.nextInt(BG_W), random.nextInt(BG_H));
        }
    }

    private static void cutPiece(BufferedImage background, BufferedImage slider, int x, int y) {
        for (int i = 0; i < PIECE; i++) {
            for (int j = 0; j < PIECE; j++) {
                int px = x + i;
                int py = y + j;
                if (px >= BG_W || py >= BG_H) {
                    continue;
                }
                slider.setRGB(i, j, background.getRGB(px, py));
            }
        }
    }

    private static void dimHole(Graphics2D g, int x, int y) {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(x, y, PIECE, PIECE, 8, 8);
        g.setColor(new Color(255, 255, 255, 90));
        g.drawRoundRect(x, y, PIECE - 1, PIECE - 1, 8, 8);
    }

    private static String toPngBase64(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成滑块图失败", e);
        }
    }
}
