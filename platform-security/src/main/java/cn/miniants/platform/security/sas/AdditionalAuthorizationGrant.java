package cn.miniants.platform.security.sas;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * 项目向内核 SAS 追加自定义 grant（如学校协议）。内核只内置 password。
 */
public interface AdditionalAuthorizationGrant {

    AuthenticationConverter converter();

    AuthenticationProvider provider();
}
