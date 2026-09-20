package cn.miniants.platform.security.qr;

/**
 * 扫码会话状态：INIT → SCANNED → SUCCESS。
 */
public enum QrLoginStatus {
    INIT,
    SCANNED,
    SUCCESS
}
