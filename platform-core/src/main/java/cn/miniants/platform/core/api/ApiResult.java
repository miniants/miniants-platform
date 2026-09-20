package cn.miniants.platform.core.api;

import cn.miniants.platform.core.error.ErrorCode;
import cn.miniants.platform.core.error.PlatformCodes;
import cn.miniants.platform.core.error.PlatformException;

import java.io.Serializable;

/**
 * 与现网四端一致的返回体。{@code code} 必须是 number。
 */
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long code;
    private T data;
    private String message;
    private String errorDetails;

    public ApiResult() {
    }

    public static <T> ApiResult<T> ok(T data) {
        return result(data, PlatformCodes.SUCCESS);
    }

    public static <T> ApiResult<T> failed(String message) {
        return result(null, PlatformCodes.FAILED.code(), message);
    }

    public static <T> ApiResult<T> failed(String message, T data) {
        return result(data, PlatformCodes.FAILED.code(), message);
    }

    public static <T> ApiResult<T> failed(ErrorCode errorCode) {
        return result(null, errorCode);
    }

    public static <T> ApiResult<T> result(T data, ErrorCode errorCode) {
        return result(data, errorCode.code(), errorCode.defaultMessage());
    }

    public static <T> ApiResult<T> result(T data, long code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.code = code;
        result.data = data;
        result.message = message;
        return result;
    }

    public boolean ok() {
        return PlatformCodes.SUCCESS.code() == code;
    }

    /**
     * Feign / 调用方取业务数据。{@code code != 200} 抛 {@link PlatformException}，不静默返回 null。
     */
    public T requireData() {
        if (!ok()) {
            throw new PlatformException(message == null || message.isBlank()
                    ? PlatformCodes.FAILED.defaultMessage()
                    : message);
        }
        return data;
    }

    public long getCode() {
        return code;
    }

    public void setCode(long code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
