package cn.miniants.platform.demo;

import cn.miniants.platform.security.sas.PlatformRegisteredClientRepository;
import cn.miniants.platform.security.sas.keystore.RotatableJwkSource;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PlatformDemoAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void platformSecurityMakesBootDefaultsBackOffWithoutApplicationExcludes() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBean(RegisteredClientRepository.class))
                .isInstanceOf(PlatformRegisteredClientRepository.class);
        assertThat(context.getBean(JWKSource.class))
                .isInstanceOf(RotatableJwkSource.class);
        assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
        assertThat(context.getBean("authorizationServerSecurityFilterChain"))
                .isInstanceOf(SecurityFilterChain.class);
        assertThat(context.getBean("platformAppSecurityFilterChain"))
                .isInstanceOf(SecurityFilterChain.class);
        assertThat(context.containsBean("defaultSecurityFilterChain")).isFalse();
    }
}
