package cn.miniants.platform.core.error;

/**
 * 分层错误码：HTTP 体仍用 {@link #code()}（200 / -1 / 301 兼容现网）；
 * {@link #key()} 给日志与 i18n，不进四端必读字段。
 */
public interface ErrorCode {

    long code();

    String key();

    String defaultMessage();
}
