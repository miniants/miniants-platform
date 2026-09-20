package cn.miniants.platform.admin.seed;

import cn.miniants.platform.data.PlatformDataProperties;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 首次初始化一套新库时，插入一个能登录的管理员（可选再插一个客户端）。
 *
 * <p>走 {@link ApplicationRunner} 而不是 {@code InitializingBean}：要确保排在 Flyway 建表之后。
 *
 * <p>已存在同名账号就整体跳过，不改口令——否则把种子配置留在 yml 里的项目，每次重启都会把
 * 管理员口令重置回配置值，线上改过的口令会被悄悄改回来。
 */
public class AdminSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminSeedProperties seed;
    private final String userTable;
    private final String clientTable;

    public AdminSeedRunner(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
            PlatformAdminSeedProperties seed, PlatformDataProperties dataProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.seed = seed;
        String prefix = dataProperties.jdbcTablePrefix();
        this.userTable = prefix + "user";
        this.clientTable = prefix + "oauth_client";
    }

    @Override
    public void run(ApplicationArguments args) {
        if (seed.getPassword() == null || seed.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "开启了 platform.admin.seed 但没给 password。基座不内置默认口令，请显式配置。");
        }
        seedAdmin();
        seedClient();
    }

    private void seedAdmin() {
        if (exists(userTable, "username", seed.getUsername())) {
            log.info("管理员 {} 已存在，跳过种子初始化", seed.getUsername());
            return;
        }
        jdbcTemplate.update("INSERT INTO " + userTable
                        + " (id, username, password, display_name, status, sys_admin, version, create_by, create_time)"
                        + " VALUES (?, ?, ?, ?, 1, 1, 1, 'system', CURRENT_TIMESTAMP)",
                IdWorker.getId(), seed.getUsername(), passwordEncoder.encode(seed.getPassword()),
                seed.getDisplayName());
        log.warn("已创建初始管理员 {}，请首次登录后立刻改口令，并从配置中移除 platform.admin.seed",
                seed.getUsername());
    }

    private void seedClient() {
        PlatformAdminSeedProperties.Client client = seed.getClient();
        if (client.getClientId() == null || client.getClientId().isBlank()) {
            return;
        }
        if (client.getClientSecret() == null || client.getClientSecret().isBlank()) {
            throw new IllegalStateException(
                    "配了 platform.admin.seed.client.client-id 但没给 client-secret。");
        }
        if (exists(clientTable, "client_id", client.getClientId())) {
            log.info("客户端 {} 已存在，跳过种子初始化", client.getClientId());
            return;
        }
        jdbcTemplate.update("INSERT INTO " + clientTable
                        + " (id, client_id, client_secret, client_name, grant_types, scopes,"
                        + " access_token_ttl, refresh_token_ttl, status, version, create_by, create_time)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 'system', CURRENT_TIMESTAMP)",
                IdWorker.getId(), client.getClientId(), passwordEncoder.encode(client.getClientSecret()),
                client.getClientName(), client.getGrantTypes(), client.getScopes(),
                client.getAccessTokenTtl(), client.getRefreshTokenTtl());
        log.warn("已创建初始客户端 {}", client.getClientId());
    }

    private boolean exists(String table, String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count != null && count > 0;
    }
}
