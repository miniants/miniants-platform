package cn.miniants.platform.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnboundAccountTest {

    @Test
    void messageOmitsHintsWhenNothingKnown() {
        assertEquals("未绑定账号", UnboundAccount.message(null, null));
        assertEquals("未绑定账号", UnboundAccount.message("  ", "未绑定"));
    }

    @Test
    void messageUsesOpenIdTailAndUsername() {
        assertEquals("未绑定账号（openid后8位=uvwxyz12）",
                UnboundAccount.message("abcdefghijklmnopqrstuvwxyz12", "未绑定"));
        assertEquals("未绑定账号（学工号=M202640301）",
                UnboundAccount.message(null, "M202640301"));
        assertEquals("未绑定账号（openid后8位=openid-1，学工号=M202640301）",
                UnboundAccount.message("openid-1", "M202640301"));
    }

    @Test
    void tailKeepsShortValues() {
        assertEquals("abc", UnboundAccount.tail("abc", 8));
        assertEquals("23456789", UnboundAccount.tail("123456789", 8));
    }

    @Test
    void exceptionUsesUnboundCode() {
        UnboundAccountException ex = UnboundAccount.exception("abcdefghijklmnopqrstuvwxyz12", null);
        assertEquals(SecurityCodes.UNBOUND, ex.errorCode());
        assertEquals("未绑定账号（openid后8位=uvwxyz12）", ex.getMessage());
        assertTrue(UnboundAccount.matches(ex.getMessage()));
        assertFalse(UnboundAccount.matches("用户名或密码错误"));
        assertFalse(UnboundAccount.matches(null));
    }
}
