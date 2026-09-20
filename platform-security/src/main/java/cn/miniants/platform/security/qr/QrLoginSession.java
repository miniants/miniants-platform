package cn.miniants.platform.security.qr;

/**
 * 扫码会话。兼容现网 Redis JSON：clientId / openId / designatedUsername。
 */
public record QrLoginSession(
        String scene,
        QrLoginStatus status,
        String initiatingClientId,
        String provider,
        String subject,
        String designatedUsername
) {
}
