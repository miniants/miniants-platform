---
title: 限流
status: active
owner: platform
last_verified: 2026-09-06
---

# 限流

产品级限流在独立模块中，不再属于 `platform-integration`。默认随 `platform-starter`（MVC）或 `platform-starter-webflux` 进入应用；没有 `@RateLimit` 且没有编程调用时不改变请求行为。

## 1. 模块

| 模块 | 职责 |
|---|---|
| `platform-ratelimit-core` | 策略模型、GCRA / 滑动窗口本地引擎、注解、内置策略 SPI、可信客户端地址、指标与 Actuator |
| `platform-ratelimit-redis` | 同步 / Reactive Redis Lua；动态快照消费；`backend=redis` 缺依赖时启动失败 |
| `platform-ratelimit-webmvc` | `@RateLimit` + Interceptor + HTTP 429 |
| `platform-ratelimit-webflux` | WebFilter / 函数式路由 + HTTP 429 |
| `platform-ratelimit-admin` | 可选控制面：表、管理 API、revision 发布 |
| `platform-ratelimit-admin-ui` | 预构建静态控制台 JAR，由 admin 传递依赖，不进入默认 starter |

## 2. 声明式

命名策略与内联策略互斥；混用或找不到命名策略会拒绝启动。

```java
@RateLimit(policy = "auth.login", subject = RateLimitSubjectType.CLIENT_IP)
@RateLimit(limit = 20, period = "1m", subject = RateLimitSubjectType.CLIENT_IP)
```

复杂主体使用具名 `RateLimitSubjectResolver` Bean，不要写 SpEL。默认只信直连地址；仅当 peer 命中 `platform.ratelimit.trusted-proxies` 才解析 `Forwarded` / `X-Forwarded-For`。

拒绝时返回真实 HTTP 429、`Retry-After`、`RateLimit-Limit/Remaining/Reset`，响应体仍为 `ApiResult(code=-1)`。

## 3. MVC 使用

`platform-starter` 已聚合 `platform-ratelimit-webmvc`。在 Controller 方法或类上声明 `@RateLimit` 即可。Interceptor 在 Handler 匹配后执行，按 `HandlerMethod` 取注解。

```java
@RestController
@RequestMapping("/login")
class LoginController {
    @RateLimit(policy = "auth.login", subject = RateLimitSubjectType.CLIENT_IP)
    @PostMapping
    public void login(@RequestBody LoginBody body) {
        // ...
    }
}
```

编程调用注入 `cn.miniants.platform.ratelimit.spi.RateLimiter`，根据 `RateLimitDecision.allowed()` 决定是否继续。不要再用已删除的 `cn.miniants.platform.integration.ratelimit`。代码内置策略实现 `RateLimitPolicyContributor`。

## 4. WebFlux 使用

`platform-starter-webflux` 聚合 `platform-ratelimit-webflux`，不含 Servlet 安全链、`platform-observability`、`platform-ops` 或 `platform-ratelimit-admin`。注解控制器由启动期 `RateLimitRouteIndex` 建路径索引，`RateLimitWebFilter` 默认 `order=-50`（鉴权之后）。函数式路由用 `HandlerFilterFunction`：

```java
RouterFunctions.route()
    .POST("/sms/send", this::send)
    .filter(RateLimitHandlerFilterFunction.of(webFilter, bindings))
    .build();
```

也可以 `RateLimitRouteIndex.registerPredicate(...)` 把同一套绑定挂到 Filter 路径匹配。WebFlux 路径使用 `ReactiveRateLimiter`，不要在该栈调用阻塞 Redis API。`backend=redis` 时必须有 `ReactiveStringRedisTemplate`，禁止同步 Redis 兜底。`USER_ID` 仅在过滤器位于鉴权之后且 `CurrentUser` 在 classpath 时允许启动。

WebFlux `Flux<T>` 成功体一次包装为 `ApiResult<List<T>>`，不会逐元素产生多个信封。

## 5. 策略来源

优先级：数据库动态快照 > YAML 基线 > 代码内置（`RateLimitPolicyContributor`）。`backend` 必须是 `local` 或 `redis`，禁止无提示退化成本地。动态快照超过 `stale-snapshot-max-age` 后按该策略的 `ALLOW` / `DENY` 故障语义执行。陈旧判断看「最近一次成功读取策略源」的时间。

```yaml
platform:
  ratelimit:
    backend: local
    key-prefix: "platform:ratelimit:"
    stale-snapshot-max-age: 5m
    snapshot-ttl: 10m
    refresh-interval: 30s
    trusted-proxies: []
    policies:
      auth.login:
        algorithm: GCRA
        limit: 20
        period: 1m
        store-failure-policy: DENY
```

`backend=redis` 需要 classpath 上有 `StringRedisTemplate`。缺依赖或未装配时启动失败。普通数据面节点启动即拉取 Redis 快照，随后监听 `policy:events` 并按 `refresh-interval` 对账续期。Redis 键空间：`platform:ratelimit:c:{policy}:{subject}`、`platform:ratelimit:policy:snapshot`、`platform:ratelimit:policy:revision`、`platform:ratelimit:policy:events`。可选管理面实时历史另用 `{prefix}live:snapshots` 与 `{prefix}live:sample:{timeSlot}`，不进计数键。计数键主体为消毒后的明文（去控制字符、最长 128；IPv6 可含冒号，解析时只在 `c:` 后第一个 `:` 切开策略与主体）。没有旧 `platform:rate:` 兼容层，也没有哈希键兼容层；换明文后旧桶随 TTL 过期即可。

## 6. 管理控制台部署

默认关闭，不进入 `platform-starter`。引入 `platform-ratelimit-admin` 后打开开关（会传递 `platform-ratelimit-admin-ui`）：

```groovy
implementation 'cn.miniants.platform:platform-ratelimit-admin'
```

```yaml
platform:
  ratelimit:
    admin:
      enabled: true
      standalone: false
      ui:
        enabled: true
        path: /platform/ratelimit
        # 可选。采用方把 Bearer 放在 sessionStorage 时填键名，例如 JWY 的 stormwind_Authorization
        # authorization-storage-key: stormwind_Authorization
      live:
        enabled: false
        sample-interval: 30s
        retention: 1h
```

- 独立 Flyway：`ratelimit_schema_history`，脚本在 `classpath:db/ratelimit-migration`；修订 JSON 列为 MySQL 8 `LONGTEXT`。
  宿主库几乎都不是空库，`baselineOnMigrate` 必须 `baselineVersion=0`，否则默认基线 1 会跳过 `V1__*`。
  已误基线的环境靠 `V2__ensure_*`（`CREATE TABLE IF NOT EXISTS`）补建。
- 权限码：`platform:ratelimit:page` / `save` / `publish`。
- 管理 API：`/platform/admin/rate-limit/policies`（page / save / enable / disable / republish / revisions / rollback / runtime / buckets / buckets/history）。乐观锁冲突（含启停）返回 HTTP 409，并附带服务端最新版本。修订列表不携带 `snapshotJson`，详情与 diff 按需查询。
  `runtime` 含本机 `availablePolicies` 与 `effectivePolicies`（编码、额度、来源 `yaml` / `dynamic` / `builtin`），控制台「当前生效」用它对照后台命名策略；「已保存」仍只列入库行。
  `GET .../buckets`（`platform:ratelimit:page`）列出当前后端全部计数桶：策略、明文主体、算法、剩余/额度、`retryAfterMs`。Redis 用 `SCAN {prefix}c:*`（禁止 `KEYS`）；本地扫内存 map。默认最多 500 条，超出标 `truncated`。剩余次数由 GCRA TAT / 滑动窗口成员按策略换算，不是 Redis 里现成数字。
  可选实时历史（`admin.live.enabled`，平台默认关）：有 Redis 时后台按 `sample-interval` 把同一帧 `RateLimitBucketsVo` 写入 `{prefix}live:snapshots`（ZSET，score=`observedAt`）。`retention` 最长 1h，且 `retention / sample-interval <= 120`。多实例用 `{prefix}live:sample:{timeSlot}` `SET NX` 争同一时隙，不写热路径、不改 `/buckets`。standalone / 无 Redis 不采，history 为空。
  `GET .../buckets/history` 返回最近至多 120 帧，供控制台进入「实时数据」时预载；之后仍轮询 `/buckets`。读失败或未启用返回空列表。明文主体因此最多多留 1h，权限仍是 `platform:ratelimit:page`。
- 多实例必须有 Redis；单机演示才允许 `admin.standalone=true`（记 WARN）。事务提交后先更新本机 LKG，再尝试发布；发布失败不回滚库，由对账任务重试同一修订。
- UI 只挂载配置路径，不接管 `/`。`{uiPath}` 无尾斜杠会 302 到 `{uiPath}/`。
  页面读同源 `{uiPath}/config.json`；`apiBase` 为相对路径
  `../admin/rate-limit/policies`，以便经网关或 Vite 前缀时仍打到同一前缀下的管理 API。
  `{uiPath}/config.json` 为 `@PublicAccess`。若 `authorization-storage-key` 有值，
  控制台从 sessionStorage / localStorage 读 Bearer（兼容 JSON 字符串），再带
  `Authorization` 调管理 API。应用安全链默认 `X-Frame-Options: SAMEORIGIN`，
  便于同源 iframe。采用方无需安装 Node。
  控制台把「当前生效」与「实时数据」分成两个 Tab。实时数据是时序折线图：
  每只桶一条线（策略 × 明文主体），纵轴剩余额度，横轴为轮询时刻。
  进入 Tab 时若 history 有数据则预载最近 retention；之后本机继续按轮询累积约 120 个点。
  自动刷新间隔可选 1s / 5s / 10s / 30s / 60s。history 失败则与现在一样从空线开始。
- 停用是明确的“不执行限流”，保留审计；不允许硬删除正在发布的策略，回滚走 revision。

## 7. 可观测

- 指标：`platform.ratelimit.acquire`、`acquire.latency`、`store.error`、`stale.policy`、`publish.error`；标签仅 policy / algorithm / backend / outcome。
- 拒绝日志带 traceId、policy、明文主体、retryAfter。主体与 Redis 计数键同一套消毒规则。
- `pl.access` reason 形如 `rate-limit:{policy}:{outcome}`。
- Actuator：`/actuator/ratelimit`（backend、revision、最近成功刷新、陈旧、sourceUnavailable、publishFailed、availablePolicies）。
- Health：`ratelimit` contributor，覆盖同样字段。
