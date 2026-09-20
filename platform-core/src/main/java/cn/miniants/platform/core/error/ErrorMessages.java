package cn.miniants.platform.core.error;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 把 {@link ErrorCode#key()} 解析成当前语言的文案。
 *
 * <p>顺序是应用文案 → 内核文案 → {@link ErrorCode#defaultMessage()}。应用排在前面，
 * 是为了让项目能改掉内核的措辞而不必改内核。
 */
public class ErrorMessages {

    private final MessageSource applicationMessages;
    private final MessageSource platformMessages;

    public ErrorMessages(MessageSource applicationMessages, MessageSource platformMessages) {
        this.applicationMessages = applicationMessages;
        this.platformMessages = platformMessages;
    }

    public String resolve(ErrorCode errorCode) {
        return resolve(errorCode, LocaleContextHolder.getLocale());
    }

    public String resolve(ErrorCode errorCode, Locale locale) {
        if (errorCode == null) {
            return PlatformCodes.FAILED.defaultMessage();
        }
        String key = errorCode.key();
        if (key == null || key.isBlank()) {
            return errorCode.defaultMessage();
        }
        String fromApplication = lookup(applicationMessages, key, locale);
        if (fromApplication != null) {
            return fromApplication;
        }
        String fromPlatform = lookup(platformMessages, key, locale);
        return fromPlatform != null ? fromPlatform : errorCode.defaultMessage();
    }

    private static String lookup(MessageSource source, String key, Locale locale) {
        if (source == null) {
            return null;
        }
        // 传 null 兜底而不是 key 本身：否则找不到时会把 key 当文案返回，前端展示成一串点号
        return source.getMessage(key, null, null, locale == null ? Locale.getDefault() : locale);
    }
}
