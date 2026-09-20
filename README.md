---
title: Miniants Platform
status: active
owner: platform
last_verified: 2026-09-20
---

# Miniants Platform

Spring Boot 4.1 / JDK 21 的后端基座内核。把每个新项目都要重写一遍的那些东西——返回体、错误码、鉴权、审计、traceId、分布式原语——做成默认就对的约定。

Apache-2.0。当前版本 `2.0.0-rc.1`。产品名 Miniants Platform；Maven 坐标仍是 `cn.miniants.platform:platform-*`。

**MVC-first。** `platform-starter` 是完整 WebMVC 栈。`platform-starter-webflux` 只覆盖数据面与限流，不含 security / observability / ops / admin。

## 快速开始

新项目引一个聚合 starter 就够：

```groovy
implementation 'cn.miniants.platform:platform-starter'
```

完整起步路径（复合构建挂载、必配项、建表与首个账号、上线前核对）见 [起步文档](./doc/00-architecture/20-getting-started.md)。

## 模块

| 模块 | 职责 |
|---|---|
| `platform-bom` | 依赖版本对齐 |
| `platform-starter` | MVC 依赖聚合，新项目引这一个 |
| `platform-starter-webflux` | WebFlux 依赖聚合 |
| `platform-core` | 返回体、分层错误码与 i18n、JSON Long 安全序列化（不传递 WebMVC） |
| `platform-core-webmvc` / `platform-core-webflux` | 各栈异常与成功体包装 |
| `platform-data` | MyBatis-Plus 约定、审计、乐观锁、逻辑删除、`EntityQuery` 白名单、DataScope、地理坐标 TypeHandler、业务库 `platform.flyway` |
| `platform-security` | `CurrentUser`、`@Permission` / `@PublicAccess` / `@Authenticated`、`@OperLog`、BFF 门面、`/auth/me`、`/auth/password`、JWT 验票、SAS |
| `platform-admin` | 内核表 Flyway + 用户/角色/客户端/租户/资源/字典/配置/操作日志参考 CRUD；种子初始化 |
| `platform-tenant` | 租户上下文；独立库 / 独立 schema / 判别列，默认关 |
| `platform-observability` | `traceId` 入站 / 跨线程 / 出站透传；MDC `traceId|uid|ip`；`pl.access`（含 auth/reason）；`LoggerNameConverter`；慢 SQL；Micrometer |
| `platform-integration` | 出站 HTTP、Secret SPI、锁 / 幂等（有 Redis 走 Redis，否则退单机） |
| `platform-ratelimit-*` | 限流引擎、Web 适配、可选管理面 |
| `platform-ops` | `/internal/runtime` 的 drain / state；仅 loopback；停机翻排空 |
| `platform-storage` | 对象存储；接口不出现厂商类型，批量操作走 `unwrap` |
| `platform-queue` | Redis 连续队列 |
| `platform-demo` | 参考应用，不是库，不发布 |

`starter` 聚合 core / core-webmvc / data / security / observability / integration / ops / ratelimit-webmvc。`storage`、`queue`、`admin`、`tenant`、`ratelimit-admin` 按需单独引。

## 构建与验证

正式消费走 Maven Central / BOM，不要把本仓库 `include` 进采用方 `allprojects`。
本地联调可以 `includeBuild` 外部源码路径，但不得作为默认 CI 路径。

```bash
./gradlew testAll
```

## 相关

- [平台文档入口](./doc/README.md)
- [起步：基于内核起一个新项目](./doc/00-architecture/20-getting-started.md)
- [内核设计](./doc/00-architecture/10-kernel.md)
- [参与开发](CONTRIBUTING.md)（含 Controller 明文实现：不要 CRUD 基类继承） · [安全问题反馈](SECURITY.md)
- [Controller 明文实现](./doc/20-standards/controller.md)
