package cn.miniants.platform.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggerNameConverterTest {

    @Test
    void padsShortKeepsMidTruncatesLongFromLeft() {
        assertEquals("p6spy     ", LoggerNameConverter.format("p6spy"));
        assertEquals(10, LoggerNameConverter.format("p6spy").length());
        assertEquals("jwy.access", LoggerNameConverter.format("jwy.access"));
        assertEquals(10, LoggerNameConverter.format("jwy.access").length());
        assertEquals("pl.access ", LoggerNameConverter.format("pl.access"));
        assertEquals(10, LoggerNameConverter.format("pl.access").length());
        assertEquals("pl.slow-sql         ", LoggerNameConverter.format("pl.slow-sql"));
        assertEquals(20, LoggerNameConverter.format("pl.slow-sql").length());
        assertEquals("jwy.outbound        ", LoggerNameConverter.format("jwy.outbound"));
        assertEquals(20, LoggerNameConverter.format("jwy.outbound").length());
        assertEquals("SysUserServiceImpl  ", LoggerNameConverter.format(
                "cn.edu.ustb.jwy.system.service.impl.SysUserServiceImpl"));
        assertEquals(20, LoggerNameConverter.format(
                "cn.edu.ustb.jwy.system.service.impl.SysUserServiceImpl").length());
        assertEquals("pl.wx     ", LoggerNameConverter.format(
                "cn.binarywang.wx.miniapp.api.impl.BaseWxMaServiceImpl"));
        assertEquals("pl.wx     ", LoggerNameConverter.format(
                "me.chanjar.weixin.common.util.http.BaseWxHttp"));
        assertEquals("inUserDetailsService", LoggerNameConverter.format(
                "cn.edu.ustb.jwy.auth.identity.WebAdminUserDetailsService"));
        assertEquals(20, LoggerNameConverter.format(
                "cn.edu.ustb.jwy.auth.identity.WebAdminUserDetailsService").length());
    }
}
