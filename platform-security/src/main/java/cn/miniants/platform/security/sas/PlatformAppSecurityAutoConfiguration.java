package cn.miniants.platform.security.sas;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * classpath 有 Spring Security 时，避免 Boot 默认 302 {@code /login}。
 * 业务鉴权仍走 JWT Filter + {@code PermissionInterceptor}。
 * 应用已有同名 bean 时不覆盖；其它 {@link SecurityFilterChain}（如 SAS）
 * 用更低 {@code @Order} 先匹配。
 */
@AutoConfiguration(after = PlatformSasAutoConfiguration.class)
@ConditionalOnWebApplication
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
public class PlatformAppSecurityAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = "platformAppSecurityFilterChain")
    public SecurityFilterChain platformAppSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}
