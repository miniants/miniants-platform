# Changelog

本文件从 `0.1.0-SNAPSHOT` 起记录 Miniants Platform semver 变更。JWY 采用清单见采用方仓库，不作为本产品文档。

## Unreleased

### Breaking

- **去掉 JWY 专属语义**：删除 `LoginAudits.USTB_AUTH` 与 `/auth/open/miniapp/login`。外部身份只保留 `/auth/open/{audience}/external` 与 `/{audience}/refresh`。采用方兼容口自己挂。
- **管理口不再双挂 `sys:*`**：参考 CRUD 只认 `platform:*`。采用方用权限别名或菜单迁移消化存量码。

### 修复

- **无 Redis 也可装配 admin**：`ConfigSource` 不再把 `StringRedisTemplate` 写进主配置类签名。

## 2.0.0-rc.1 — 2026-09-20

拆分准备：品牌改为 Miniants Platform，坐标仍为 `cn.miniants.platform`，BOM 约束全部发布模块。

## Previous

下列条目来自 2.0 开发窗口，正式 GA 时并入 `2.0.0`。

### 修复

- **客户端 4xx 不再伪装 503**：缺参（`ServletRequestBindingException`）、读不出 body、缺 multipart 段、非 multipart 请求落 HTTP 400。兜底再认 Spring `ErrorResponse` 的 4xx（415 / 413 / 406 等）保持原状态码，WARN、不打栈。WebFlux 对齐。

### 可观测

- 换票盖戳增加 `login=unbound`：未绑定是预期下一步，`pl.access` 与 `PlatformSecurityAdvice` 保持 INFO；只有 `login=fail` 升 WARN。采用方抛 `UnboundAccount.exception`。
- 请求 MDC 增加 `ip`（`X-Forwarded-For` 首段 / `X-Real-IP` / remoteAddr）。控制台前缀 `[traceId|uid|ip]`，文件前缀追加 ip。`pl.access` 正文不再重复 `ip=`。只读请求头，无额外 I/O。

### Breaking

- **2.0.0 限流切到独立产品模块**：删除 `cn.miniants.platform.integration.ratelimit`、固定窗口算法与 `platform:rate:` 键。请改用 `@RateLimit` / `cn.miniants.platform.ratelimit.spi.RateLimiter` 与 `platform.ratelimit.*`。
- **限流计数键与拒绝日志改为明文主体**：Redis 键 `{prefix}c:{policy}:{subject}`，不再写 SHA-256 截断哈希。旧哈希桶无兼容层，随 TTL 过期即可。
- **`platform-core` 不再传递 WebMVC**。MVC 应用应依赖 `platform-starter` 或同时引入 `platform-core-webmvc`。WebFlux 使用 `platform-starter-webflux`。

### 新增

- 字典参考 CRUD 增加可选 `content`、按 `parentId` 分页、`POST /check`、删分类前拦子项；根节点未传 `dictType` 时用自身编码，子项用父级编码。
- 操作日志列表支持 `collapse=true`：按时间倒序后连续相同用户 / IP / URI / 摘要折一行。
- 字典与操作日志管理口双挂现网码：`platform:dict:*|sys:dict:*`、`platform:oper-log:page|sys:operLog:page`。
- `ExternalIdentityBinding.unbindByPerson`：软删某人在指定 provider 下的外部身份。
- GCRA / 滑动窗口，本地与 Redis（含 Reactive），真实 HTTP 429，YAML/数据库动态策略，可选管理控制台。设计见 `doc/00-architecture/55-rate-limit.md`。
- 普通数据面节点可独立消费 Redis 策略快照；revision CAS、快照 TTL/续期、内置策略 `RateLimitPolicyContributor`。
- `platform-ratelimit-admin-ui` 作为可发布静态资源 JAR，由 admin 传递依赖。
- 限流控制台可通过 `platform.ratelimit.admin.ui.authorization-storage-key` 从
  sessionStorage 读取 Bearer（给不把 JWT 写入 Cookie 的采用方）。`config.json` 公开。
- 控制台 `config.json` 的 `apiBase` 改为相对路径，适配网关/Vite 前缀；`/platform/ratelimit`
  重定向到带尾斜杠。应用安全链 `X-Frame-Options` 为 SAMEORIGIN，便于同源 iframe。
- 限流控制台「当前生效 / 实时数据」两个 Tab；实时数据为剩余额度时序折线图，自动刷新 1s–60s。
  `GET /platform/admin/rate-limit/policies/buckets` 供控制台轮询。
- 可选实时历史：`platform.ratelimit.admin.live`（默认关）后台按间隔把 `/buckets` 整帧写入 `{prefix}live:snapshots`，最长 1h、最多约 120 帧。
  `GET .../buckets/history` 供控制台进入 Tab 时预载；不改限流 Lua 与 `/buckets` 契约。

### 审计

- 登录 / 换票经 `SecurityAuditSink.login(LoginAttempt)` 落库：密码、刷新、小程序码、扫码出票、公开 `/oauth/token` 的 `client_credentials` / 已拒 grant / 密钥失败。
- 鉴权 deny / unclassified 补齐 `http_status`（shadow 放行 200，enforce 拒绝 401/403）与 `request_summary` 的 `enforcement=` / `jwt=invalid`。
- 坏票记 `permission-unauthenticated:bad-token`。

### 迁移

- 限流管理面使用独立 Flyway 历史 `ratelimit_schema_history`（`classpath:db/ratelimit-migration`），不占用 `platform_schema_history`。只有引入 `platform-ratelimit-admin` 并打开 `platform.ratelimit.admin.enabled` 才会建表。修订列使用 MySQL 8 `LONGTEXT`。
- WebFlux starter 不再传递 `platform-observability` / `platform-ops`（避免带入 Servlet）。`backend=redis` 时 WebFlux 必须有 Reactive Redis。
- 动态管理多实例必须有 Redis；单机才允许 `platform.ratelimit.admin.standalone=true`。
- 采用方若自己实现 `SecurityAuditSink`，宜覆盖 `login(request, LoginAttempt)`。

## 1.0.2 — 2026-08-27

### 修复

- **`PlatformJwtCustomizer.resolveUserAccount`** 改为 `public`，供采用方 `OAuth2TokenCustomizer`（如 JWY `JwyPlatformJwtCustomizer`）跨包复用；修复 1.0.1 编译错误。

### 迁移

- 无新 Flyway。自 1.0.1 升级直接替换内核即可。

## 1.0.1 — 2026-08-27

### 修复

- **SAS 进程内出票**：`AccountTokenIssuer` 使用 `ResolvedAccountGrant` 携带 `UserAccount`，JWT 定制器在首次出票时可解析 `real_auth` / `openId` 等 claim；修复 1.0.0 微信 / USTB / 扫码登录身份列表缺失。
- **`PlatformJwtCustomizer.resolveUserAccount`**：统一解析顺序（`ResolvedAccountGrant` → `PasswordGrant` → authorization 属性）；`JwyPlatformJwtCustomizer` 复用同一入口。
- **JDBC 持久化**：仍用 `UserAccounts.forAuthorizationStore` + 无 details 的 principal；编码期与持久化期分离，不再靠删 `setDetails` 规避序列化。

### 迁移

- 无新 Flyway。自 1.0.0 升级直接替换内核即可；已在 1.0.0 遇微信身份问题的环境须发本版。

## 1.0.0 — 2026-08-27

### 安全与语义（P1）

- **`platform.security.reject-unclassified`**（默认 `true`）：`enforce` 下拒绝未分类接口；JWY 等存量采用方须在 dest 显式 `false`。
- **租户路由**：未知租户键 fail-fast（`lenientFallback=false`），空上下文且无 default 拒启。
- **幂等 / 限流**：Local 与 Redis 统一为 null 或非正 TTL/window 拒绝并打日志。
- **自然人**：`sys_person.id_lookup_digest` 唯一约束（V9）；并发 `verifyOrCreate` 依赖 DB UNIQUE。
- **Admin 校验**：`UserSave` / `RoleSave` / `OauthClientSave` 加 Jakarta Validation；脏 role/resource ID 拒绝。
- **SAS**：生产缺 keystore 拒启（`allow-ephemeral-keys` 仅 demo）；有 DataSource 时使用 JDBC 授权持久化（V10 `oauth2_authorization`）。

### 出站与审计

- 操作日志：去掉 DELETE；`record` 由服务端填 `operatorId` / `traceId`，客户端不可伪造。
- `IllegalArgumentException` 不再回显原文。
- Token 响应 claim 白名单（含 JWY 需要的 `principals`、`openId`）。
- BFF `clientKey` 默认不信 `X-Forwarded-For`；`platform.security.bff.challenge.trust-forwarded-for` 显式开启。

### 健壮性

- `EntityQuery` `$in` 非 Collection 返回 400。
- DataScope / 表前缀启动期装配可见（WARN）。
- 失效总线 listener 异常隔离（Local 对齐 Redis）。

### 迁移

- 平台 Flyway：V9 person UNIQUE、V10 oauth2 授权表。
- JWY Flyway：V9 oauth2、V10 person UNIQUE（MySQL 条件 DDL）；发布日 refresh 作废一次。

### 明确不在本版

- Maven Central / NOTICE 门禁、覆盖率红灯、微信 HTTP 门面下沉、`sys:*` 双码清零、第二采用方 runbook。
