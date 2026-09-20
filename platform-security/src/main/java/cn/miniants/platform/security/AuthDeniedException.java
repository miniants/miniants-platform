package cn.miniants.platform.security;

import cn.miniants.platform.core.error.ErrorCode;
import cn.miniants.platform.core.error.PlatformException;
import org.springframework.http.HttpStatus;

public class AuthDeniedException extends PlatformException {

    private final HttpStatus status;

    public AuthDeniedException(ErrorCode errorCode, HttpStatus status) {
        super(errorCode);
        this.status = status;
    }

    public AuthDeniedException(ErrorCode errorCode, HttpStatus status, String message) {
        super(errorCode, message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
