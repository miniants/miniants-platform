package cn.miniants.platform.security.sas;

import cn.miniants.platform.security.account.UserAccount;
import cn.miniants.platform.security.account.UserAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.h2.Driver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformSasJdbcAuthorizationTest {

    @Test
    void jdbcAuthorizationRoundTripPreservesUserAccountAttribute() {
        var dataSource = new SimpleDriverDataSource(new Driver(),
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE oauth2_authorization (
                    id VARCHAR(100) NOT NULL PRIMARY KEY,
                    registered_client_id VARCHAR(100) NOT NULL,
                    principal_name VARCHAR(200) NOT NULL,
                    authorization_grant_type VARCHAR(100) NOT NULL,
                    authorized_scopes TEXT NULL,
                    attributes TEXT NULL,
                    state VARCHAR(500) NULL,
                    authorization_code_value TEXT NULL,
                    authorization_code_issued_at TIMESTAMP NULL,
                    authorization_code_expires_at TIMESTAMP NULL,
                    authorization_code_metadata TEXT NULL,
                    access_token_value TEXT NULL,
                    access_token_issued_at TIMESTAMP NULL,
                    access_token_expires_at TIMESTAMP NULL,
                    access_token_metadata TEXT NULL,
                    access_token_type VARCHAR(100) NULL,
                    access_token_scopes TEXT NULL,
                    refresh_token_value TEXT NULL,
                    refresh_token_issued_at TIMESTAMP NULL,
                    refresh_token_expires_at TIMESTAMP NULL,
                    refresh_token_metadata TEXT NULL,
                    oidc_id_token_value TEXT NULL,
                    oidc_id_token_issued_at TIMESTAMP NULL,
                    oidc_id_token_expires_at TIMESTAMP NULL,
                    oidc_id_token_metadata TEXT NULL,
                    user_code_value TEXT NULL,
                    user_code_issued_at TIMESTAMP NULL,
                    user_code_expires_at TIMESTAMP NULL,
                    user_code_metadata TEXT NULL,
                    device_code_value TEXT NULL,
                    device_code_issued_at TIMESTAMP NULL,
                    device_code_expires_at TIMESTAMP NULL,
                    device_code_metadata TEXT NULL
                )
                """);

        RegisteredClient client = RegisteredClient.withId("1")
                .clientId("demo")
                .clientSecret("{noop}secret")
                .authorizationGrantType(PlatformGrantTypes.PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder().build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(1))
                        .build())
                .build();
        RegisteredClientRepository repository = new RegisteredClientRepository() {
            @Override
            public void save(RegisteredClient registeredClient) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RegisteredClient findById(String id) {
                return "1".equals(id) ? client : null;
            }

            @Override
            public RegisteredClient findByClientId(String clientId) {
                return "demo".equals(clientId) ? client : null;
            }
        };

        var jsonMapper = PlatformSasJsonMapper.create();
        JdbcOAuth2AuthorizationService service =
                new JdbcOAuth2AuthorizationService(jdbc, repository);
        service.setAuthorizationRowMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(repository, jsonMapper));
        service.setAuthorizationParametersMapper(
                new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(jsonMapper));

        UserAccount account = new UserAccount(1L, "alice", "{noop}x", "Alice", true, false, java.util.List.of(10L), null);
        account = UserAccounts.forAuthorizationStore(account);
        var principal = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated("alice", null, AuthorityUtils.NO_AUTHORITIES);

        Instant now = Instant.now();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "access-1", now, now.plusSeconds(3600));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-1", now, now.plusSeconds(86400));

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName("alice")
                .authorizationGrantType(PlatformGrantTypes.PASSWORD)
                .attribute(Principal.class.getName(), principal)
                .attribute(UserAccount.ATTR, account)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        service.save(authorization);

        OAuth2Authorization loaded = service.findByToken("refresh-1",
                org.springframework.security.oauth2.server.authorization.OAuth2TokenType.REFRESH_TOKEN);
        assertNotNull(loaded);
        assertEquals("alice", loaded.getPrincipalName());
        assertInstanceOf(UserAccount.class, loaded.getAttribute(UserAccount.ATTR));
        assertEquals("alice", ((UserAccount) loaded.getAttribute(UserAccount.ATTR)).username());
    }
}
