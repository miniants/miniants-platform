---
title: 架构文档索引
status: active
owner: platform
last_verified: 2026-08-26
---

# 架构文档索引

- [内核设计](10-kernel.md)：模块职责、数据与安全约定、`sys_config` 运行期配置、验收基线。
- [基于内核起步](20-getting-started.md)：依赖方式、最小配置、建表与初始化。
- [本人数据归属](30-owned-data.md)：`@Authenticated(resolver=…)` 与 `OwnedResolver`。
- [登录能力](40-login.md)：SAS、BFF、自然人、多账号、扫码与外部身份。
- [Redis 连续队列](50-continuous-queue.md)：队列模型、键契约、工人和排空。
- [限流](55-rate-limit.md)：GCRA / 滑动窗口、MVC 与 WebFlux、动态策略与可选控制台。
- [当前架构决策](60-decisions.md)：仍有效的边界、发布、扩展与兼容约束。

工程实现还应遵守 [Controller](../20-standards/controller.md)、[JSON Long](../20-standards/json-long-as-string.md) 和 [AutoConfiguration](../20-standards/autoconfigure.md) 约定。
