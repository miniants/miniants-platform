package cn.miniants.platform.core.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorMessagesTest {

    private static ResourceBundleMessageSource platformBundle() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setDefaultEncoding("UTF-8");
        source.setBasename("messages/platform-errors");
        return source;
    }

    private static ErrorMessages withApplicationMessages(StaticMessageSource application) {
        return new ErrorMessages(application, platformBundle());
    }

    @Test
    void resolvesFromThePlatformBundle() {
        ErrorMessages messages = withApplicationMessages(new StaticMessageSource());

        assertEquals("权限不足", messages.resolve(SecurityCodesProbe.FORBIDDEN, Locale.CHINA));
        assertEquals("Permission denied", messages.resolve(SecurityCodesProbe.FORBIDDEN, Locale.ENGLISH));
    }

    @Test
    void applicationTextOverridesThePlatformWording() {
        StaticMessageSource application = new StaticMessageSource();
        application.addMessage("platform.security.forbidden", Locale.CHINA, "没有该操作的授权");

        assertEquals("没有该操作的授权",
                withApplicationMessages(application).resolve(SecurityCodesProbe.FORBIDDEN, Locale.CHINA));
    }

    @Test
    void unknownKeyFallsBackToTheDefaultMessageRatherThanShowingTheKey() {
        ErrorCode custom = new SimpleErrorCode(-1, "app.orders.not-payable", "订单当前状态不可支付");

        assertEquals("订单当前状态不可支付",
                withApplicationMessages(new StaticMessageSource()).resolve(custom, Locale.CHINA));
    }

    @Test
    void missingLocaleDoesNotBlowUp() {
        assertEquals("操作失败",
                new ErrorMessages(null, platformBundle()).resolve(PlatformCodes.FAILED, null));
    }

    @Test
    void nullCodeDegradesToTheGenericFailure() {
        assertEquals("操作失败",
                withApplicationMessages(new StaticMessageSource()).resolve(null, Locale.CHINA));
    }

    /** platform-security 不在 core 的测试类路径上，这里复刻它的码来验证同一个资源包。 */
    private static final class SecurityCodesProbe {
        static final ErrorCode FORBIDDEN =
                new SimpleErrorCode(-1, "platform.security.forbidden", "权限不足");
    }
}
