---
title: JSON Long 安全序列化规范
status: active
owner: platform
last_verified: 2026-08-26
---

# JSON Long 安全序列化规范

本规范与 JWY 采用方保持同一 HTTP 契约。

## 规则

MVC 响应中的 `Long` / `long` 仅当绝对值大于 JavaScript `Number.MAX_SAFE_INTEGER`（`2^53-1`，即 `9007199254740991`）时序列化为字符串。内核通过 `JsSafeLongSerializer` 和 `PlatformJacksonAutoConfiguration` 注册该行为。

这样既避免雪花 ID 在浏览器丢精度，也保留小整数为 JSON number。尤其是 `ApiResult.code` 必须保持 `200`，不能变为 `"200"`。

| 方向 | 契约 |
|---|---|
| HTTP 响应 | 超大 Long → string；安全范围内 Long/long → number |
| HTTP 请求 | number 或 string 均可反序列化为 Long/long |
| 不影响 | Redis 序列化、JWT 构造所用 ObjectMapper、数据库与 Java 内部类型 |

`Integer`、`Boolean` 等类型保持原样。

## 客户端约定

- 业务主键、外键和角色 ID 按不透明值传递；禁止 `Number(id)` 后再回传。
- 比较时统一为字符串或使用不会发生数值转换的比较方式。
- 判断是否存在 ID 不得使用 truthy；`id=0` 合法。
- 业务状态码继续按 number 处理。

## 禁止

- 把所有 Long 无差别转成字符串。
- 在前端把雪花 ID 转成 Number。
- 用散落的 `ToStringSerializer` 注解替代全局边界策略。
- 把 MVC 的 Long 模块复用到 Redis、JWT 等不同协议的 ObjectMapper。
