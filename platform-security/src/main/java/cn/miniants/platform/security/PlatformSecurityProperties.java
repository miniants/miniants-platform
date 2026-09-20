package cn.miniants.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.security")
public class PlatformSecurityProperties {

    /**
     * Filter 层协议 / 基础设施匿名口。OAuth、登录、actuator、error、运维口不能标
     * {@link PublicAccess}，由这里放行。业务公开口不要写进来。
     */
    public static final List<String> DEFAULT_ANONYMOUS_PATHS = List.of(
            "/actuator/**",
            "/*/actuator/**",
            "/internal/runtime/**",
            "/error",
            "/oauth/**",
            "/login",
            "/login/**"
    );

    /**
     * 是否装配内核鉴权 Filter / 拦截器。只要类型、不要这套拦截链的采用方可关。
     */
    private boolean enabled = true;

    /**
     * {@code off} / {@code shadow} / {@code enforce}。默认 shadow。
     */
    private String enforcement = "shadow";

    /**
     * {@code enforce} 下是否拒绝未分类接口（无 {@code @Permission} / {@code @Authenticated}）。
     * 默认 true；仍在扫描未分类口的采用方可显式配 false。
     */
    private boolean rejectUnclassified = true;

    /**
     * 用请求头注入 {@link CurrentUser}。只给 demo / 单测，默认关。
     */
    private boolean headerAuth = false;

    /**
     * 资源服务器 RSA 公钥。有 {@code JwtDecoder} Bean（例如 SAS）时不会再用这里装配。
     */
    private final Jwt jwt = new Jwt();

    /**
     * 追加到 {@link #DEFAULT_ANONYMOUS_PATHS}。业务公开口用 {@link PublicAccess}。
     */
    private List<String> anonymousPaths = new ArrayList<>();

    /**
     * 本人数据归属（{@code @Authenticated(resolver=…)}）。
     */
    private final Owned owned = new Owned();

    /**
     * 自然人模型（{@code sys_person}）。缺省关；开启且缺 {@code PersonRegistry} 时启动失败。
     */
    private final Person person = new Person();

    /**
     * 账号侧行为（一人多 principals 等）。
     */
    private final Account account = new Account();

    /**
     * 外部身份 provider 开关。开启任一 provider 且缺 {@code ExternalIdentityBinding} 时启动失败。
     */
    private final ExternalId externalId = new ExternalId();

    /**
     * OAuth 客户端发票读路径 Redis 缓存（有 {@code StringRedisTemplate} 时装配）。
     */
    private final OauthClientCacheSettings oauthClientCache = new OauthClientCacheSettings();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEnforcement() {
        return enforcement;
    }

    public void setEnforcement(String enforcement) {
        this.enforcement = enforcement;
    }

    public boolean isRejectUnclassified() {
        return rejectUnclassified;
    }

    public void setRejectUnclassified(boolean rejectUnclassified) {
        this.rejectUnclassified = rejectUnclassified;
    }

    public boolean isHeaderAuth() {
        return headerAuth;
    }

    public void setHeaderAuth(boolean headerAuth) {
        this.headerAuth = headerAuth;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public List<String> getAnonymousPaths() {
        return anonymousPaths;
    }

    public void setAnonymousPaths(List<String> anonymousPaths) {
        this.anonymousPaths = anonymousPaths == null ? new ArrayList<>() : new ArrayList<>(anonymousPaths);
    }

    /**
     * 内核默认协议口加上项目追加的口。
     */
    public List<String> resolvedAnonymousPaths() {
        LinkedHashSet<String> merged = new LinkedHashSet<>(DEFAULT_ANONYMOUS_PATHS);
        for (String path : anonymousPaths) {
            if (path != null && !path.isBlank()) {
                merged.add(path.trim());
            }
        }
        return List.copyOf(merged);
    }

    public Owned getOwned() {
        return owned;
    }

    public Person getPerson() {
        return person;
    }

    public Account getAccount() {
        return account;
    }

    public ExternalId getExternalId() {
        return externalId;
    }

    public OauthClientCacheSettings getOauthClientCache() {
        return oauthClientCache;
    }

    /** 本人数据归属（{@code @Authenticated(resolver=…)}）装配与行为。 */
    public static class Owned {

        /**
         * 是否装配 {@link OwnedInterceptor} / {@link OwnedAnnotationRegistrar}。默认开。
         */
        private boolean enabled = true;

        /**
         * {@code sysAdmin} 是否跳过归属校验。默认 true（对齐现网超管放行）；跳过时
         * {@link OwnedContext#subject()} 为 null。
         */
        private boolean adminBypass = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAdminBypass() {
            return adminBypass;
        }

        public void setAdminBypass(boolean adminBypass) {
            this.adminBypass = adminBypass;
        }
    }

    public static class Jwt {

        private String publicKey;
        private String publicKeyLocation;
        private String jwkJson;

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPublicKeyLocation() {
            return publicKeyLocation;
        }

        public void setPublicKeyLocation(String publicKeyLocation) {
            this.publicKeyLocation = publicKeyLocation;
        }

        public String getJwkJson() {
            return jwkJson;
        }

        public void setJwkJson(String jwkJson) {
            this.jwkJson = jwkJson;
        }

        boolean hasSource() {
            return hasText(publicKey) || hasText(publicKeyLocation) || hasText(jwkJson);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    /** 自然人模型开关与证件摘要 pepper。 */
    public static class Person {

        private boolean enabled = false;

        /**
         * 证件号摘要可选 pepper；空则仅哈希规范化 (类型, 证号)。
         */
        private String pepper = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPepper() {
            return pepper;
        }

        public void setPepper(String pepper) {
            this.pepper = pepper == null ? "" : pepper;
        }
    }

    /** 账号侧开关。 */
    public static class Account {

        /**
         * 登录响应带 principals（含仅 1 个账号），并接受 designated_username。缺省关；
         * 开启且缺 {@code PrincipalDirectory} 时启动失败。
         */
        private boolean multiPrincipal = false;

        public boolean isMultiPrincipal() {
            return multiPrincipal;
        }

        public void setMultiPrincipal(boolean multiPrincipal) {
            this.multiPrincipal = multiPrincipal;
        }
    }

    /**
     * 外部身份。YAML：{@code platform.security.external-id.providers.wechat.enabled=true}。
     */
    public static class ExternalId {

        private Map<String, Provider> providers = new LinkedHashMap<>();

        public Map<String, Provider> getProviders() {
            return providers;
        }

        public void setProviders(Map<String, Provider> providers) {
            this.providers = providers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providers);
        }

        public boolean anyProviderEnabled() {
            return providers.values().stream().anyMatch(p -> p != null && p.isEnabled());
        }

        public static class Provider {

            private boolean enabled = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    public static class OauthClientCacheSettings {

        private String redisKeyPrefix = "platform:oauth:client:";
        private Duration ttl = Duration.ofMinutes(10);

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix == null || redisKeyPrefix.isBlank()
                    ? "platform:oauth:client:"
                    : redisKeyPrefix;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
        }
    }
}
