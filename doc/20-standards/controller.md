---
title: Controller 明文实现规范
status: active
owner: platform
last_verified: 2026-08-26
---

# Controller 明文实现规范

Controller 不继承 CRUD 基类，不靠父类方法注册 URI，也不靠类级规则推导权限码。

每个对外方法在本类明确写出：

- HTTP 映射；
- `@Permission`、`@PublicAccess`、`@Authenticated` 或 `@Authenticated(resolver=…)`；
- 入参绑定、Service 调用和返回值。

JSON 请求体必须标 `@RequestBody`。GET 与表单对象使用 Spring 标准绑定，不使用隐式 JSON 参数解析。

只实现实际需要的接口，不因通用 CRUD 形状自动暴露增删改查。分页、filter 解码和实体白名单查询可以抽成普通辅助类，但辅助类不得注册 URI。

禁止引入：

- `BaseController` 或等价 CRUD Controller 基类；
- “继承即挂口”的接口或默认方法；
- 按 HTTP 方法、类名或包名推导权限码的机制；
- 把展示层按钮控制当成服务端授权。

`platform-admin` 的参考 CRUD 同样遵守本规范。本人归属接口与权限码接口不得在同一方法上混标，见 [Owned](../00-architecture/30-owned-data.md)。
