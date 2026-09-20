---
title: 2.0 兼容面
status: active
owner: platform
last_verified: 2026-09-06
---

# 2.0 兼容面

本文列出 2.0.0 起视为**公开、可依赖**的契约基线。Breaking change 须先更新本文与 CHANGELOG，并附测试。

## HTTP 信封

- 路径：全进程统一 `ApiResult`（`code` / `data` / `message` / `errorDetails`）。
- 成功 `code=200` 为 JSON number；超大 Long 见 [JSON Long 规范](../20-standards/json-long-as-string.md)。
- 未识别异常：HTTP 503 + 固定文案；`IllegalArgumentException`：HTTP 400 + 「请求参数不正确」。
- 缺必填请求值 / 请求体读不出 / 非 multipart：HTTP 400 + 「请求参数不正确」，不当 503。
- 方法不匹配：HTTP 405 + 「不支持的请求方法」。其它 Spring `ErrorResponse` 4xx 保持原状态码（406 / 413 / 415 等），不当 503。

## 公开 REST（platform 模块）

| 前缀 | 说明 |
|---|---|
| `/platform/admin/**` | 内核管理参考实现（用户/角色/资源/客户端/字典/配置/操作日志） |
| `/platform/admin/rate-limit/**` | 可选限流策略管理（引入 `platform-ratelimit-admin` 且开启后） |
| `/platform/ratelimit/**` | 可选限流控制台（`admin.ui.enabled`）；`/config.json` 提供同源 API base |
| `/auth/open/web/**` | BFF 密码 / 刷新 |
| `/auth/open/{audience}/external` `/refresh` | 外部身份换票与刷新 |
| `/oauth2/token` 等 | SAS 协议端点（`platform.security.sas` 开启时） |

## 公开 SPI / 扩展点

| 类型 | 包 |
|---|---|
| `PersonRegistry` / `PrincipalDirectory` / `ExternalIdentityBinding` | `platform-security` person/identity |
| `ExternalIdentityProvider` | 渠道换码（采用方实现） |
| `OauthCatalog` | 客户端 grant/scope _catalog 扩展 |
| `OwnedResolver` | 本人数据归属 |
| `OperLogRecorder` | 操作审计 |
| `ObjectStorage` | 对象存储 |
| `TenantResolver` | 租户解析（采用方显式提供） |
| `RateLimitPolicyContributor` | 代码内置限流策略 |

## 配置键（默认值）

| 键 | 默认 | 说明 |
|---|---|---|
| `platform.security.enforcement` | `shadow` | `off` / `shadow` / `enforce` |
| `platform.security.reject-unclassified` | `true` | enforce 下是否拒绝未分类 |
| `platform.security.sas.enabled` | `false` | SAS |
| `platform.security.sas.allow-ephemeral-keys` | `false` | 无 keystore 临时密钥（仅 demo） |
| `platform.security.bff.challenge.trust-forwarded-for` | `false` | 验证码 clientKey 是否信 XFF |
| `platform.data.table-prefix` | `sys_` | JDBC / Flyway 表前缀 |
| `platform.tenant.strategy` | `none` | 租户策略 |
| `platform.flyway.enabled` | `false` | 业务 Flyway |
| `platform.queue.prefix` | （必填） | Redis 队列键前缀，须以 `:` 结尾 |
| `platform.ratelimit.admin.ui.authorization-storage-key` | （空） | 控制台从 sessionStorage 读 Bearer 的键 |
| `platform.ratelimit.admin.live.enabled` | `false` | 管理面是否后台采样桶快照历史 |
| `platform.ratelimit.admin.live.sample-interval` | `30s` | 后台采样间隔；与 retention 之比不得超过 120 |
| `platform.ratelimit.admin.live.retention` | `1h` | 历史最长保留，上限 1h |

## 内核 DDL（Flyway V1–V10）

- 管理表：`sys_user`、`sys_role`、`sys_resource`、`sys_oauth_client`、`sys_oper_log` 等。
- V8：`sys_person`、`sys_external_identity`。
- V9：`sys_person.id_lookup_digest` UNIQUE。
- V10：`oauth2_authorization`、`oauth2_authorization_consent`。
- V13：`sys_dict.content`（可选扩展内容）。

## Redis 协议

- 队列键布局：`{prefix}{queueName}:ready|delay|job|lock|index|wake`（见 `QueueKeyFactory`）。
- 集成：`platform:idem:` / `platform:lock:` 等为实现默认前缀，可配 `platform.integration.redis.key-prefix`。
- `sys_config` 缓存默认 `platform:config:`，可配 `platform.admin.config.redis-key-prefix`。
- OAuth 客户端缓存默认 `platform:oauth:client:`，可配 `platform.security.oauth-client-cache.redis-key-prefix`。
- 限流：`platform:ratelimit:*`（计数 / 策略快照 / 事件）。计数键 `{prefix}c:{policy}:{subject}` 为明文主体，不再写 SHA-256 截断哈希。已删除 `platform:rate:` 与 `cn.miniants.platform.integration.ratelimit`；哈希计数键无兼容层。
- 限流管理：`GET /platform/admin/rate-limit/policies/buckets` 列出当前后端全部计数桶（最多 500）。
- 限流实时历史（`admin.live.enabled`）：`{prefix}live:snapshots`（整帧 ZSET）、`{prefix}live:sample:{timeSlot}`（时隙去重）。`GET /platform/admin/rate-limit/policies/buckets/history` 返回最近至多 120 帧。

## 不在兼容面

- demo 专用配置、测试 `header-auth`、内核 JavaDoc 缺口、MinIO 细节。
- WebFlux 安全 / 可观测 / 运维 / 管理面：当前只承诺 MVC。
