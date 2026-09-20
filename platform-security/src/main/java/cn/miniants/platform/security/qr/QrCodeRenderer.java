package cn.miniants.platform.security.qr;

/**
 * 把 scene 画成码。缺省实现可抛不支持；微信无限码由应用提供。
 */
public interface QrCodeRenderer {

    byte[] render(String scene);
}
