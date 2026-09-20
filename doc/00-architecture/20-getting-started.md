---
title: 基于平台内核起步
status: active
owner: platform
last_verified: 2026-09-20
---

# 基于平台内核起步

## 1. 引入内核

正式路径是 Maven 坐标，不要把内核源码嵌进业务仓：

```groovy
dependencies {
    implementation platform('cn.miniants.platform:platform-bom:2.0.0-rc.1')
    implementation 'cn.miniants.platform:platform-starter'
}
```

本地改内核时才允许显式 `includeBuild('/path/to/miniants-platform')`。默认 CI 与发布不得走 composite。

本仓库验收：

```bash
./gradlew testAll
```

**MVC-first。** 新项目默认引 `platform-starter`。`platform-starter-webflux` 只覆盖数据面与限流，不含 security / observability / ops / admin。

## 2. 选择模块

`platform-starter` 聚合 core、core-webmvc、data、security、observability、integration、ops、ratelimit-webmvc。WebFlux 用 `platform-starter-webflux`（不含 Servlet 可观测/运维模块）。以下能力按需单独引入：

- `platform-admin`：内核管理表、参考 CRUD 与首个账号初始化。
- `platform-ratelimit-admin`：限流策略管理 API 与可选控制台。
- `platform-tenant`：多租户隔离。
- `platform-storage`：对象存储。
- `platform-queue`：Redis 连续队列。

授权服务器能力位于 `platform-security`，但相关依赖按需启用；只有承担发票职责的应用才打开 SAS/BFF。资源服务器不应顺带开启登录栈。

## 3. 最小配置

```yaml
platform:
  core:
    i18n:
      default-locale: zh_CN
  data:
    table-prefix: sys_
  security:
    enforcement: shadow
```

- 新项目先用 `shadow` 收集未分类和权限不匹配，再切 `enforce`。无票访问非公开口在两种模式下都返回 401。
- SAS、BFF、tenant、person 等能力默认关闭；使用属性启停，不要通过排除整组 AutoConfiguration 拆能力。
- 业务公开口标 `@PublicAccess`；只有协议级匿名路径才追加到 `platform.security.anonymous-paths`。

外部身份与扫码最小片段：

```yaml
platform:
  security:
    sas:
      public-denied-grants: [password, external, qr]
    bff:
      external:
        enabled: true
        audiences:
          miniapp:
            client-id: APP_MINIAPP
            provider: APP_MINIAPP
      qr:
        enabled: true
        confirm-channel: miniapp
        confirm-provider: APP_MINIAPP
        channels:
          web:
            client-id: APP_WEB
```

## 4. 建表与初始化

引入 `platform-admin` 后，内核管理表使用独立的 `platform_schema_history`。业务表迁移使用 `platform.flyway`，默认关闭：

```yaml
platform:
  flyway:
    enabled: true
    datasource: master
    table: flyway_schema_history
    locations:
      - classpath:db/migration
    baseline-on-migrate: true
    baseline-version: "1"
```

不要让 `platform.flyway` 与同一目标库上的 `spring.flyway.enabled=true` 同时迁业务表。

内核建表脚本不含固定账号和口令。首次初始化可临时打开：

```yaml
platform:
  admin:
    seed:
      enabled: true
      username: admin
      password: ${ADMIN_INIT_PASSWORD}
```

口令没有默认值；缺失时启动失败。账号已存在时初始化整体跳过，不重置口令。完成初始化后应移除 seed 配置。

## 5. 上线前核对

- JSON Long 只把超出 JS 安全范围的值写成字符串，见 [JSON Long](../20-standards/json-long-as-string.md)。
- 多实例显式分配不重复的雪花机器位，或确认默认推导不会碰撞。
- 多实例使用锁、幂等、限流、失效总线或队列时，确认 Redis Bean 已装配；否则只具备单进程语义。
- 队列必须显式确定稳定的键前缀；改变前缀会让已有任务不可见。
- Controller 显式声明 URI 和鉴权，不继承 CRUD Controller。
- 错误优先使用 `PlatformException(ErrorCode)`；未知内部异常不向客户端回显。
- `/internal/runtime/**` 不经公网或不可信反向代理暴露。
