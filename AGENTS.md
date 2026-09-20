---
title: Miniants Platform 开发约定
status: active
owner: platform
last_verified: 2026-09-20
---

# Miniants Platform

适用于本仓库。设计见 [平台内核](./doc/00-architecture/10-kernel.md)，文档入口见 [doc/README.md](./doc/README.md)。参与方式见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## Controller

参考 CRUD（`platform-admin`）和业务项目自己的 Controller：**不要**做 CRUD 基类继承。URI 与 `@Permission` 写在本类方法上，只实现真正用到的接口。见 [Controller 明文实现规范](./doc/20-standards/controller.md)。
