---
title: 当前架构决策
status: active
owner: platform
last_verified: 2026-09-16
---

# 当前架构决策

本文只记录当前仍有效、会约束后续实现的决策，不保留阶段性统计、整改进度或长变更记录。

## 1. 内核边界

1. 内核使用中性包名 `cn.miniants.platform.*`，面向跨行业复用。
2. 判断能力能否进入内核的标准是：换一个项目，代码无需修改仍然正确。
3. 具体权限码、组织名称、内部拓扑、渠道协议和密钥、业务枚举及业务种子数据留在采用方。
4. 表结构和可替换的参考实现可以进入内核；扩展字段优先用扩展表，避免把某个前端或行业模型写入主表。
5. 通用能力终局只保留内核实现；采用方可通过 SPI、`@ConditionalOnMissingBean` 或组合层扩展，不复制第二套框架。

## 2. 构建与发布

1. 内核采用 Spring Boot 4.1、JDK 21、Gradle 8.14 独立构建。HTTP JSON 走 Jackson 3；MVC 仍只把超大 Long 写成字符串。
2. 源码联调使用 Gradle composite build，不把内核并入采用方统一 `allprojects`。
3. composite build 的根 `test` 不覆盖 included build；必须执行 `jwy-platform/gradlew testAll` 或显式聚合任务。
4. `platform-demo` 是验收应用，不作为库发布。
5. `platform-starter` 只聚合普遍需要且具备安全默认值的模块；storage、queue、admin、tenant、ratelimit-admin 按需引入。WebFlux 应用使用 `platform-starter-webflux`，禁止把 Servlet 安全链带入。

## 3. Web 与安全

1. 一个进程只允许一套 `ApiResult` 信封与异常映射。
2. 未识别异常不向客户端回显内部 message；日志用 traceId 关联。
3. Controller 不继承 CRUD 基类；URI 与鉴权注解在本类方法上明文声明。
4. 权限语义只认 `@PublicAccess`、`@Authenticated`、Owned 和 `@Permission`，不按包名或 URL 推导角色。
5. `shadow` 只观察已认证主体的权限不匹配、未分类或 Owned 失败；匿名访问非公开口始终 401。`reject-unclassified` 默认 true，enforce 下未分类拒绝。
6. JWT 保持瘦票，权限码在资源侧展开；用户用权限码，设备客户端用 scope。
7. 登录通用状态机和 SPI 在 platform；学校 SSO、渠道 SDK、账号来源及业务 claim 留采用方。BFF-only grant 使用中性名 `external` / `qr`，不把渠道名写进内核 grant。
8. SAS 开启且存在 DataSource 时使用 JDBC 授权持久化；生产缺 keystore 拒启（demo 可 `allow-ephemeral-keys`）。

## 4. 数据与序列化

1. 新表使用小写蛇形全称和可配置 `sys_` 前缀；主键为 BIGINT 雪花，`id=0` 合法。
2. 逻辑删除使用 nullable timestamp，乐观锁使用 version，审计人由 `AuditorSupplier` 提供。
3. 内核管理迁移使用独立 `platform_schema_history`；业务迁移使用 `platform.flyway` 且默认关闭。
4. 动态查询必须按实体字段白名单，DataScope 不接受原始 SQL。
5. HTTP JSON 仅将超出 JavaScript 安全整数范围的 Long 写成字符串；小 Long 保持 number。
6. Redis、JWT 和非 MVC ObjectMapper 不继承 MVC Long 序列化策略。

## 5. 基础设施与扩展

1. AutoConfiguration 通过 `AutoConfiguration.imports` 直挂，不通过应用注解 `@Import` 或空壳转发。
2. Bean 条件不能替代自动装配顺序；存在 MissingBean 竞争时必须声明 `before` / `after` 并测试。
3. Redis 可用时锁、幂等和失效广播使用分布式实现；无 Redis 时退化为单机语义，文档和日志必须明确。
   限流是独立产品：`backend` 必须显式为 `local` 或 `redis`，`redis` 缺依赖时启动失败，不做隐式本地回退。
   阻塞 I/O 批处理使用 `platform-integration` 的有界执行器；并发数、线程名和
   饱和策略由采用方配置，不使用 JVM common pool。
4. 缓存失效 channel、队列前缀和 wake channel 都是稳定协议；运行中的应用不得随意改名。
5. `ObjectStorage` 核心接口只接受 JDK 与平台类型，不暴露厂商、Servlet、Multipart
   或 Spring Web 类型；HTTP 上传下载由独立 `ObjectStorageWebAdapter` 承接，
   `platform-storage` 不传递 Web starter。
6. `/internal/runtime/**` 只允许直接 loopback 访问，不信任转发头，不替代 Actuator。
7. 不把具体调度器、支付报文加密、学校身份解析或前端路由模型伪装成通用内核能力。
8. 平台不默认信任身份或租户请求头：header-auth 只在测试 classpath 生效；
   租户 header 绑定必须由采用方显式提供可信 `TenantResolver`。

## 6. 兼容与演进

1. 公开 API、表、配置默认值和安全语义变化必须先更新本目录文档并有测试。
2. 危险能力默认关闭；启用能力但缺关键 SPI 时启动失败，不静默降级。
3. 口令升级只在验证成功后回写，回写失败不打断已通过的登录。
4. 种子初始化没有默认口令，且只用于首次初始化；已存在账号不重置。
5. 长期决策写在本文；历史迁移过程和采用方兼容记录写在采用方文档。
6. 运行期 `sys_config` 读写走 `ConfigSource`；是否缓存由每次调用的 `ConfigLookup` 指定。采用方不要再为单条配置手写 Redis + Pub/Sub。管理端改配置必须 `evict`。
