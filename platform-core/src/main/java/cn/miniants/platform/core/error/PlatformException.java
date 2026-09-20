package cn.miniants.platform.core.error;

public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final boolean messageOverridden;

    public PlatformException(String message) {
        super(message);
        this.errorCode = PlatformCodes.FAILED;
        this.messageOverridden = true;
    }

    public PlatformException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
        this.messageOverridden = false;
    }

    public PlatformException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.messageOverridden = true;
    }

    public PlatformException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = PlatformCodes.FAILED;
        this.messageOverridden = true;
    }

    public PlatformException(Throwable cause) {
        super(cause);
        this.errorCode = PlatformCodes.FAILED;
        this.messageOverridden = true;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * 调用方是否给了具体文案。给了就照原样回，因为它往往带上下文（"学号 20230001 已存在"），
     * 比按 key 查出来的通用句子有用。
     */
    public boolean messageOverridden() {
        return messageOverridden;
    }
}
