---
title: 平台内核设计
status: active
owner: platform
last_verified: 2026-09-16
---

# 平台内核设计

Miniants Platform 是 Spring Boot 4.1、JDK 21、Gradle 8.14 的独立构建，包名为 `cn.miniants.platform.*`。它提供跨项目可复用的后端基座，不承载采用方的业务语义、部署拓扑或渠道实现。

## 1. 构建与边界

- 内核自带 Gradle 构建，不要作为普通子项目 `include` 进采用方的 `allprojects`；本地源码联调使用 Gradle composite build。
- 内核只允许出现接口、抽象、通用实现和配置驱动的安全默认值。
- 具体权限码、组织名称、内部地址、渠道密钥项、业务枚举和种子业务数据不得进入内核。
- 表结构和参考实现可以进入内核；业务数据与业务适配留在采用方。
- 可替换的 Service、SPI 和基础设施 Bean 使用 `@ConditionalOnMissingBean`。

## 2. 模块职责

| 模块 | 职责 |
|---|---|
| `platform-bom` | 对齐依赖版本 |
| `platform-starter` | 聚合 core、core-webmvc、data、security、observability、integration、ops、ratelimit-webmvc |
| `platform-starter-webflux` | 聚合中性 core、core-webflux、data、observability、integration、ops、ratelimit-webflux |
| `platform-core` | `ApiResult`、错误码与 i18n、JSON 与通用工具；不传递 WebMVC |
| `platform-core-webmvc` | Servlet 异常映射与成功体包装 |
| `platform-core-webflux` | WebFlux 异常与成功体适配 |
| `platform-data` | MyBatis-Plus、实体与审计、乐观锁、逻辑删除、`EntityQuery`、DataScope、业务库 Flyway |
| `platform-security` | `CurrentUser`、权限与公开注解、Owned、操作日志、JWT、SAS 与 BFF |
| `platform-admin` | 内核管理表、用户/角色/资源/客户端/租户/字典/配置/操作日志参考实现及初始化 |
| `platform-tenant` | 独立库、独立 schema、判别列三种租户隔离策略；默认关闭 |
| `platform-observability` | traceId、访问日志、跨线程与出站透传、慢 SQL、Micrometer |
| `platform-integration` | 出站 HTTP、Secret SPI、锁、幂等、本地缓存、失效广播与有界批处理 |
| `platform-ratelimit-*` | 限流引擎、Redis、Web 适配、可选管理面与控制台 |
| `platform-ops` | loopback 运行时 state/drain、排空和可开停组件生命周期 |
| `platform-storage` | 厂商中立的对象存储接口、可选后端与显式 Web 适配器 |
| `platform-queue` | Redis 连续队列、工人、延迟闹钟、孤儿回收 |
| `platform-demo` | 可运行参考应用与验收载体，不发布为库 |

`storage`、`queue`、`admin`、`tenant`、`platform-ratelimit-admin` 不在聚合 starter 中，采用方按需引入。

## 3. HTTP 与错误契约

一个进程只使用一套信封：

```json
{"code":200,"data":{},"message":"执行成功","errorDetails":null}
```

- 成功码 `200` 保持 JSON number；`ok(false)` 仍是成功返回。业务失败应抛 `PlatformException` 或显式返回失败结果。
- 未识别异常使用 HTTP 503 和固定用户文案，SQL、Redis、堆栈和内部异常 message 不得出站。
- 无 Handler / 无静态资源（`NoHandlerFoundException` / `NoResourceFoundException`）使用 HTTP 404，不当内部故障。
- 缺必填请求值或请求体读不出（`ServletRequestBindingException` 含 `MissingServletRequestParameterException`、`HttpMessageNotReadableException`、缺 multipart 段；WebFlux `ServerWebInputException`）使用 HTTP 400，WARN、不打栈，不当内部故障。文案用「请求参数不正确」，不回显参数名或内部 message。
- 方法不匹配使用 HTTP 405；其它 Spring `ErrorResponse` 4xx（如 406 / 413 / 415）保持原状态码，同样只 WARN、不打栈。非 4xx 的 `ErrorResponse`（如异步超时 503）仍按未识别内部故障处理。
- 需要跳过信封包装时显式标 `@RawBody`，禁止重复包装。
- 限流拒绝是例外：真实 HTTP 429 + 标准限流头，信封仍为 `code=-1`。见 [限流](55-rate-limit.md)。
- JSON Long 与日期格式见 [JSON Long 规范](../20-standards/json-long-as-string.md)。

## 4. 数据约定

### 4.1 表与字段

| 项 | 约定 |
|---|---|
| 表名 | 小写蛇形、单词全称；默认前缀 `sys_`，可由 `platform.data.table-prefix` 配置 |
| 主键 | `id BIGINT`，雪花；`id=0` 合法，判断持久化只用 `id != null` |
| 逻辑删除 | `deleted TIMESTAMP NULL`；未删除为 `NULL` |
| 乐观锁 | `version BIGINT` |
| 审计 | `create_id`、`create_by`、`create_time`、`update_by`、`update_time` |
| 状态 | `TINYINT`，默认 `1` 启用、`0` 停用，DDL 注释写清 |
| 密码 | `VARCHAR(128)`，存 BCrypt 或带算法前缀的安全哈希，不设独立 salt 列 |
| 约束名 | `pk_`、`uk_`、`idx_` + 表名 + 列名 |
| 内核迁移历史 | `platform_schema_history`，不占业务迁移历史表 |

内核管理表包括用户、角色、用户角色、OAuth 客户端、租户、资源、角色资源、字典、配置和操作日志；自然人及外部身份表见[登录能力](40-login.md)。`sys_dict.content` 为可选扩展内容（打印模板等），不是筛选维度。`sys_oper_log` 为追加型记录，不使用逻辑删除和乐观锁；列表可按连续相同用户 / IP / URI / 摘要折叠。

### 4.2 实体与查询

- `SuperEntity` 提供主键；`BaseEntity` 提供审计；`VersionedEntity` 提供版本；`LogicDeleteEntity` 提供逻辑删除。
- 用户资料、角色数据范围等无逻辑删除字段的一对一扩展行，在删除用户或角色的同一事务中先物理删除，再逻辑删除主表，删除后不得遗留孤儿。
- `EntityQuery` 只允许实体元数据中登记的列；联表必须显式登记别名和实体。未知字段、别名、操作符或排序方向按非法请求拒绝。
- DataScope 只在 Mapper 方法显式携带参数时生效；列名受白名单约束，值只接受 ID，不接受原始 SQL。
- 雪花机器位可通过 `platform.data.snowflake.worker-id` 与 `datacenter-id` 指定，范围均为 0–31。多实例要么全部显式配置且不重复，要么全部使用默认推导。
- 业务表迁移使用 `platform.flyway`，默认关闭；不要与同一库上的 Boot `spring.flyway` 业务迁移同时开启。

### 4.3 运行期配置（`sys_config`）

`ConfigSource` 按 `category` + `configKey` 读写 `sys_config`，给采用方当运行期功能开关，不要再各自拼 Redis。

- **真源是库**。`get` / `set` 的 `ConfigLookup` 决定这次是否走缓存：`dbOnly()` 每次读库；`cached()` / `cached(ttl)` 为本进程本地 TTL（缺省 30s）+ Redis 共享。
- **写完必广播**。无论这次 `set` 是否缓存，都删共享键并经 `InvalidationBus` 通知其它实例清本地缓存。管理端 `/platform/admin/config` 保存/删除同样 `evict`。
- Redis 键默认 `platform:config:{category}:{key}`，采用方用 `platform.admin.config.redis-key-prefix` 改前缀（教务为 `jwy:platform:config:`）。失效 topic 同名。无 Redis 时只剩本地 TTL + 进程内总线。
- 不要整表缓存，也不要把配置表无过滤地暴露给客户端。热路径（如强制鉴权）用 `cached()`；登录才读一次的项可用 `dbOnly()` 或短 TTL。

## 5. 安全约定

权限档位从低到高为：

1. `@PublicAccess`：公开业务口。
2. `@Authenticated`：要求已认证。
3. `@Authenticated(resolver=…)`：要求用户身份并通过本人归属校验。
4. `@Permission("code")`：要求用户权限码或客户端 scope。

关键规则：

- `CurrentUser` 区分 user、client、anonymous；超级管理员看 `sysAdmin`，不硬编码用户或角色 ID。
- 协议匿名口由 `PublicAccessPaths` 与 `platform.security.anonymous-paths` 管理；普通业务公开口标 `@PublicAccess`。
- `platform.security.enforcement` 支持 `off`、`shadow`、`enforce`，默认 `shadow`。无票访问非公开口在 shadow 与 enforce 下都返回 401；shadow 只放过已登录主体的权限不匹配、未分类或归属失败。
- `platform.security.reject-unclassified` 默认 `true`：在 `enforce` 下拒绝未标 `@Permission` / `@Authenticated` / `@PublicAccess` 的接口。存量采用方若仍有未分类口，须在 dest 显式 `reject-unclassified=false` 直至收敛。
- 用户权限从角色资源展开；JWT 保持瘦票，不把完整权限码塞进 token。客户端用 scope 对同一权限码。
- `@OperLog` 只标需要审计的写操作；列表、心跳、支付回调等不应默认记录。请求/响应和密钥不得写入操作日志。
- 资源归属能力见 [Owned](30-owned-data.md)，登录与出票能力见[登录能力](40-login.md)。
- 请求头主体注入只在 Boot Test classpath 生效，生产误配 `header-auth=true`
  不会注册伪造入口。租户 header 绑定不提供默认 resolver；采用方只有在可信
  网关已剥离外部同名头时，才显式声明 `HeaderTenantResolver` Bean。

## 6. 基础设施约定

- traceId 从合法入站头继承或生成 16 位十六进制值，写入响应头、MDC、异步任务和受支持的出站 HTTP。同一请求还把 uid、ip 写入 MDC，供日志前缀 `[traceId|uid|ip]`。
- `pl.access` 只记录结构化摘要，不记录请求体、签名或密钥。单行按 HTTP 分档：2xx/3xx INFO，4xx 或 `login=fail` WARN，5xx ERROR；渠道阈值仍为 INFO。换票盖戳 `login=ok|fail|unbound`：未绑定是预期下一步，保持 INFO，只有 `fail` 升 WARN。
- `platform.observability.access-log=false` 只关闭内置访问日志，不改变请求边界：`TraceIdFilter` 的最外层 `finally` 始终清理 traceId、uid 与 ip。宿主自定义访问日志必须在该过滤器内层的过滤器或请求处理链中完成，不得依赖请求离开过滤器后的 MDC。
- 有 Redis 时锁、幂等、限流和失效广播使用分布式实现；没有 Redis 时退化为单进程语义。多实例上线前必须确认实际装配。
- schema 租户路由在借出连接时优先调用 JDBC `setSchema`，驱动不支持时回退
  `setCatalog`；调用方关闭连接时先恢复借出前的 schema/catalog，再把物理连接归还连接池。
  恢复失败也必须继续关闭物理连接，避免状态污染演变为连接泄漏。
- `platform-ops` 的 `/internal/runtime/**` 只信任直接对端 loopback，不信任转发头；state 响应使用原始 body，drain 在停机前阻止工人领取新任务。
- `ObjectStorage` 核心接口只使用 JDK 与平台存储类型，不出现 Servlet、Multipart、
  Spring Web 或具体厂商类型。Web 应用通过
  `cn.miniants.platform.storage.web.ObjectStorageWebAdapter` 接收上传并写下载响应；
  特殊批量能力通过显式 `unwrap` 逃生口。
- `platform-storage` 不向采用方传递整个 Web starter；使用 Web 适配器的应用自行提供
  Spring Web 与 Servlet API，纯存储调用方不承担该依赖。

## 7. 验收基线

- `./gradlew testAll` 通过；采用方另有聚合任务覆盖 composite build 两侧。
- demo 能启动；内部异常不泄露原始 message。
- `EntityQuery` 拒绝未登记字段；DataScope 对 skip、空范围、非法列和值有测试。
- 公开、已登录、Owned、用户权限和客户端 scope 的 shadow/enforce 行为均有测试。
- SAS/BFF 能签发瘦 JWT；公开 token 端点拒绝仅限 BFF 的用户凭证 grant；密钥不回显。
- `id=0` 可正常更新；密码与客户端密钥只写不读。
- 用户、角色逻辑删除后，其无逻辑删除语义的一对一扩展表没有孤儿行。
- traceId 可跨请求、异步与出站 HTTP 透传；无论内置访问日志是否启用，MDC 都在请求结束后清理。
- Redis 与本地基础设施实现的选择、AutoConfiguration 顺序和退化语义均有装配测试。
- runtime state/drain 仅 loopback 可访问，停机先进入排空。
