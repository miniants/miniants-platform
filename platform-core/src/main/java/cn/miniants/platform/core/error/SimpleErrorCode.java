package cn.miniants.platform.core.error;

public record SimpleErrorCode(long code, String key, String defaultMessage) implements ErrorCode {
}
