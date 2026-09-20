---
title: Miniants Platform 文档
status: active
owner: platform
last_verified: 2026-09-20
---

# Miniants Platform 文档

本目录是独立仓的文档入口，只描述可跨项目复用的内核能力。具体学校、业务权限码、渠道协议、部署拓扑和迁移记录属于采用方，不进入本目录。

## 阅读顺序

1. [架构索引](00-architecture/00-index.md)
2. [内核设计](00-architecture/10-kernel.md)
3. [基于内核起步](00-architecture/20-getting-started.md)
4. [本人数据归属](00-architecture/30-owned-data.md)
5. [登录能力](00-architecture/40-login.md)
6. [Redis 连续队列](00-architecture/50-continuous-queue.md)
7. [限流](00-architecture/55-rate-limit.md)
8. [当前架构决策](00-architecture/60-decisions.md)
9. [2.0 兼容面](00-architecture/61-compatibility-surface.md)
10. 工程标准：[Controller](20-standards/controller.md) · [JSON Long](20-standards/json-long-as-string.md) · [AutoConfiguration](20-standards/autoconfigure.md)

## 文档边界

- 设计、公开契约、配置默认值或模块职责变化时，先更新本目录再改实现。
- 只记录当前有效状态，不在架构正文堆叠长 changelog。
- 采用方的业务特化写在采用方文档；JWY 的适配设计继续由父仓 `doc/` 维护。
