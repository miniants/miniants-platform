package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.InMemoryUserAccountService;
import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录成功后把存量弱哈希换成 BCrypt。这是 58644 个 MD5Salt 口令无感迁移的落点：
 * 教务的 {@code JwyPasswordEncoder} 负责认出存量并给出 {@code upgradeEncoding}，
 * 这里只钉内核这一侧的时序——什么时候写、什么时候绝对不能写。
 */
class PasswordGrantRehashTest {

    /** 用 {noop} 冒充存量弱哈希：它不是默认 id，upgradeEncoding 就报待升级。 */
    private final PasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt", Map.of(
            "bcrypt", new BCryptPasswordEncoder(),
            "noop", new PlaintextEncoder()));

    @AfterEach
    void clear() {
        AuthorizationServerContextHolder.resetContext();
    }

    @Test
    void rewritesStaleHashToBcryptOnSuccessfulLogin() {
        InMemoryUserAccountService accounts = accountsWith("{noop}secret");

        authenticate(accounts, "secret");

        String stored = accounts.findByUsername("zhang").orElseThrow().passwordHash();
        assertFalse(stored.startsWith("{noop}"), "存量弱哈希应已被换掉");
        assertTrue(encoder.matches("secret", stored), "换掉之后原口令仍然能登录");
    }

    @Test
    void leavesFreshHashUntouched() {
        String fresh = encoder.encode("secret");
        InMemoryUserAccountService accounts = accountsWith(fresh);

        authenticate(accounts, "secret");

        assertEquals(fresh, accounts.findByUsername("zhang").orElseThrow().passwordHash(),
                "已经是 BCrypt 的不该每次登录都重写，否则白付一次 BCrypt 开销和一次 UPDATE");
    }

    @Test
    void doesNotRewriteWhenPasswordWrong() {
        InMemoryUserAccountService accounts = accountsWith("{noop}secret");

        assertThrows(OAuth2AuthenticationException.class, () -> authenticate(accounts, "wrong"));

        assertEquals("{noop}secret", accounts.findByUsername("zhang").orElseThrow().passwordHash(),
                "口令错时若也重哈希，等于把库里的口令改成攻击者输入的那个");
    }

    @Test
    void loginStillSucceedsWhenRehashWriteFails() {
        UserAccountService readOnly = new UserAccountService() {
            @Override
            public Optional<UserAccount> findByUsername(String username) {
                return Optional.of(staleAccount());
            }

            @Override
            public void updatePasswordHash(Long id, String passwordHash) {
                throw new IllegalStateException("从库只读");
            }
        };

        assertEquals("token-value", tokenValue(readOnly));
    }

    /** 没接改密的应用走 {@link UserAccountService} 的缺省实现，那个方法直接抛。 */
    @Test
    void toleratesApplicationsWithoutPasswordWrite() {
        UserAccountService noWrite = username -> Optional.of(staleAccount());

        assertEquals("token-value", tokenValue(noWrite));
    }

    private static UserAccount staleAccount() {
        return new UserAccount(7L, "zhang", "{noop}secret", "张三", true, false, List.of(), null);
    }

    private static InMemoryUserAccountService accountsWith(String passwordHash) {
        return new InMemoryUserAccountService()
                .add(new UserAccount(7L, "zhang", passwordHash, "张三", true, false, List.of(), null));
    }

    private String tokenValue(UserAccountService accounts) {
        return authenticate(accounts, "secret").getAccessToken().getTokenValue();
    }

    private OAuth2AccessTokenAuthenticationToken authenticate(UserAccountService accounts, String rawPassword) {
        RegisteredClient client = RegisteredClient.withId("1")
                .clientId("web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(PlatformGrantTypes.PASSWORD)
                .scope("read")
                .build();
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return "https://example.test";
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().build();
            }
        });

        OAuth2AuthorizationService authorizationService = new InMemoryOAuth2AuthorizationService();
        OAuth2TokenGenerator<OAuth2Token> tokenGenerator = context -> new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(300));
        PasswordGrantAuthenticationProvider provider = new PasswordGrantAuthenticationProvider(
                accounts, encoder, authorizationService, tokenGenerator);

        Authentication clientPrincipal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null);
        PasswordGrantAuthenticationToken grant = new PasswordGrantAuthenticationToken(
                clientPrincipal, "zhang", rawPassword, Set.of("read"));

        return (OAuth2AccessTokenAuthenticationToken) provider.authenticate(grant);
    }

    private static final class PlaintextEncoder implements PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals(rawPassword.toString());
        }
    }
}
