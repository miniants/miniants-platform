---
title: 平台登录能力
status: active
owner: platform
last_verified: 2026-09-21
---

# 平台登录能力

`platform-security` 提供 SAS、BFF、密码登录、自然人、多账号、实名、扫码和外部身份的通用编排；`platform-admin` 提供对应的默认数据实现。具体学校协议、渠道 SDK、业务账号发现和存量口令适配属于采用方。

## 1. 目标模型

```text
BFF（密码 / 实名 / 扫码 / 外部身份）
  → SAS + UserTokenIssuer
  → 瘦 JWT
  → sys_person 1:N sys_user
  → sys_external_identity
```

- `sys_user` 是登录账号。
- `sys_person` 是自然人，一个自然人可关联多个账号。
- `sys_external_identity` 用 `(provider, subject)` 唯一关联自然人。
- `sys_user_profile` 仍是账号级资料，不承担自然人合并。

证件原文不得进入 JWT 或登录响应。实名登录的失败/成功排查日志可以带证件号、姓名、学工号。内核只保存不可逆查找摘要与可选密文；加密组件和密钥由采用方配置。

## 2. SAS 与 BFF

- SAS 支持 `client_credentials`、`refresh_token` 和扩展 grant。
- 用户凭证只交给服务端 BFF；公开 `/oauth/token` 默认拒绝 password 及采用方配置的 BFF-only grant。
- `UserTokenIssuer` 可对已解析的 `UserAccount` 直接出票，供密码、扫码、外部身份和采用方 grant 复用。
- 密码登录成功后，如 `PasswordEncoder.upgradeEncoding` 返回 true，可回写新哈希；只在验密成功后回写，回写失败不影响本次登录。
- 浏览器和小程序不得持有 OAuth client secret。
- 启用平台 SAS 的采用方不需要、也不应通过 `spring.autoconfigure.exclude` 排除 Spring Boot
  Authorization Server、JWT 或默认用户自动装配。平台先注册客户端仓储、可轮换 JWK、
  `JwtDecoder` 与安全链，Boot 默认实现按条件退让。

内核 BFF 的基础能力包括 Web 密码/刷新、当前主体与改密。实名、扫码、挑战和外部身份按属性开启。

## 3. Token 与响应契约

内核 JWT 保持瘦票，基础 claim 为：

- `userId`
- `username`
- `name`（`displayName`，空或空白回退 `username`；不得因 null claim 出票失败）
- `sysAdmin`
- `roleIds`

实名门面 `/auth/open/id-no` 盖 `login=ok|fail|unbound`，失败/成功排查日志带学工号、姓名、证件号。换票审计走 `SecurityAuditSink.login`（`event_type=login`）：密码 / 刷新由 `UserTokenIssuer` 记；外部身份 / 扫码出票带 grant 的 `issueForAccount` 记；换票前失败与公开 `/oauth/token` 由 BFF / `PublicTokenGrantFilter` 记。用户列 `用户名/clientId`，`request_summary` 写 `grant=`，`http_status` 区分成功与失败。`pl.access` 盖戳 `login=ok|fail|unbound`：出票 `ok`，未绑定 `unbound`（INFO），真失败 `fail`（WARN）。未绑定由内核 `UnboundAccount.exception` 抛出，`PlatformSecurityAdvice` 记 INFO；采用方不要自造「未绑定」文案。

完整权限码从角色资源在资源侧展开，不进入 JWT。采用方可通过 `OAuth2TokenCustomizer` 增加自己的 claim，但不得把密钥或证件原文写入 token。

多账号模式下，登录/换票响应只要能列出启用账号，就返回 `principals`（**含仅 1 个账号**，长度为 1）；空列表或查不到则不写该字段。客户端用它做选身份或单身份直接进入，不要把缺省当成「未认证」。该字段不进入 JWT。指定账号必须由 `PrincipalDirectory` 校验属于同一 person。

## 4. 扩展 SPI

| SPI | 职责 |
|---|---|
| `UserAccountService` | 查询账号、修改密码哈希、按 person 列账号 |
| `PasswordEncoder` | 验证与升级口令 |
| `PersonRegistry` | 验真、创建或合并自然人 |
| `PrincipalDirectory` | 列出同一自然人的可用账号 |
| `PersonExpansionSource` | 从采用方账号源发现应补充的账号 |
| `ExternalIdentityProvider` | 把渠道凭证解析为 provider/subject（实现须提供稳定 `id()`） |
| `ExternalIdentityProviderRegistry` | 按 `id()` 选择 Provider；多实现时 audience 须显式配置 |
| `ExternalIdentityBinding` | 外部身份与 person 的绑定 |
| `QrLoginStore` | 扫码 scene 与状态存取 |
| `QrCodeRenderer` | 把 scene 渲染成二维码 |
| `LoginChallenge` | 登录失败计数与挑战 |
| `AdditionalAuthorizationGrant` | 向 SAS 注册采用方 grant |
| `OAuth2TokenCustomizer` | 增加采用方 JWT claim |

启用自然人、多账号、外部身份或扫码能力但缺少关键 SPI 时，应用应启动失败，不能静默退化。

## 5. 开关

以下能力均应显式开启：

- `platform.security.sas.enabled`
- `platform.security.bff.enabled`
- `platform.security.bff.real-name.enabled`
- `platform.security.bff.external.enabled`
- `platform.security.bff.qr.enabled`
- `platform.security.bff.challenge.enabled`
- `platform.security.person.enabled`
- `platform.security.account.multi-principal`
- `platform.security.external-id.{provider}.enabled`

扫码状态机为 `INIT → SCANNED → SUCCESS`，各状态使用有限 TTL。Redis 键前缀必须稳定；修改前缀会切换到一套全新的登录状态空间。

## 5.1 外部身份（external）

1. `platform.security.bff.external.enabled=true`，至少一个 `ExternalIdentityProvider` Bean。
2. 配置 `platform.security.bff.external.audiences.{audience}.client-id`（OAuth client）及可选 `provider`（对应 `ExternalIdentityProvider.id()`；仅一个 Provider 时可省略）。
3. 客户端 `grant_types` 须含 `external`（通常再加 `refresh_token`）。
4. 客户端 `POST /auth/open/{audience}/external`，渠道凭证作为 form 字段；指定账号用 `designated_username`。
5. 未绑定账号时业务错误，不出游客票。这是外部身份登录的预期下一步，不是凭证失败。回给客户端的 `message` 可带已掌握的脱敏线索（`openid后8位`、`学工号`），不回完整 openid 或证件号。访问日志盖 `login=unbound`。

## 5.2 扫码（qr）

1. `platform.security.bff.qr.enabled=true`，提供 `QrLoginStore` 与 `QrCodeRenderer`。
2. 配置 `channels`（发起端 client，须含 `qr` grant）、`confirm-channel` 与 **`confirm-provider`**（与 `sys_external_identity.provider` 对齐，**不是** clientId）。
3. 流程：`GET /auth/open/{channel}/qrcode` → 确认端 `POST .../scanned` → 已登录 `POST .../auth-pass` → 发起端 `POST .../auth-query` 或 `GET .../events`（SSE）SUCCESS 出票。
4. `qr` 只给发起端；确认端不需要 `qr` grant。INIT/SCANNED 轮询 / SSE 等待不记失败审计。

### 5.2.1 发起端 SSE（可选）

`GET /auth/open/{channel}/qrcode/events?scene=`，`@PublicAccess`，`Content-Type: text/event-stream`。

- 与 `auth-query` 并存；旧客户端继续轮询，新客户端可切 SSE。
- 事件 JSON 与 `auth-query` 对齐：`INIT` / `SCANNED` 为 `{scene,status}`；`SUCCESS` 含 `jwtData`，随后关闭连接。
- 订阅时 scene 空或 Redis 已无会话：不抛异常（避免 `Accept: text/event-stream` 与 JSON Advice 打架变成 500），推一条 `{status:INVALID,message}` 后关流，与 tick 发现失效相同。`auth-query` 仍抛「二维码已失效」。
- 进程内**一个**共享 tick（约 200ms）扫本实例全部订阅：读 `QrLoginStore`，状态变则 `send`；距上次写 ≥15s 写注释心跳（:`），续 nginx 默认 60s 空闲计时。`SseEmitter` 超时 5 分钟。
- 响应头 `X-Accel-Buffering: no`；不上 Redis Pub/Sub（跨实例靠 tick 读共享键）。
- 不按连接 `new Thread()` 或 `while+sleep`。

内核 SAS 默认公开 `/oauth/token` 拒绝 `password`、`external`、`qr`。采用方可追加历史 grant 名到 `public-denied-grants`，只拒签、不作可签发别名。

## 6. 安全约束

- 密码和客户端密钥只写不读；新建客户端的明文密钥只允许一次性返回。
- 外部身份绑定、自然人创建/合并必须幂等，并对并发建立 person 或 `(provider, subject)` 粒度互斥。
- 合并自然人前先迁移全部账号与外部身份引用，禁止制造孤儿。
- 扫码确认必须由已认证用户完成，不能信任客户端提交的 openId 等身份值。
- SAS/BFF 只在发票应用启用，同一应用上下文不得挂第二套授权服务器或登录门面。

## 7. 采用方特化

学校 SSO、学校账号花名册、微信等渠道 SDK、历史口令呈现和业务 claim 不进入 platform。JWY 的具体接入与兼容契约见父仓 JWY adopter 文档。

## 8. 验收

- 密码、刷新和客户端凭证能按启用能力出票；公开 token 端点拒绝 BFF-only grant。
- JWT 不含 `principals`、证件原文和客户端密钥。
- 多账号开启时，登录响应在仅 1 个启用账号时也带长度为 1 的 `principals`。
- 指定账号必须属于同一 person，错误指定被拒。
- 扫码状态迁移、外部身份唯一性和并发绑定有测试。
- 口令只在验证成功后升级，回写失败不打断登录。
- 启用扩展而缺关键 SPI 时启动失败。
