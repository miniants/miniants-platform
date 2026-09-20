---
title: 本人数据归属能力
status: active
owner: platform
last_verified: 2026-09-03
---

# 本人数据归属能力

Owned 用于表达“已登录用户只能操作属于自己的数据”。内核负责注解、拦截、上下文、审计和拒绝响应；采用方只实现“如何解析本人”的业务规则。

## 1. API

`@Authenticated` 同时覆盖两档：

- `@Authenticated`：只要求已认证。
- `@Authenticated(resolver=SomeOwnedResolver.class, param="id")`：认证后校验资源归属。

`resolver` 缺省为 `OwnedResolver.None`。`param` 留空时解析主体本人；非空时从 query 参数或路径变量读取资源 key。

采用方实现：

```java
public interface OwnedResolver {
    Resolution resolve(OwnedRequest request);
}
```

解析结果只有三类：

- `Ok(subject)`：归属成立，并把 subject 绑定到 `OwnedContext`。
- `NotOwner(detail)`：资源存在但不属于当前用户。
- `Unresolved(detail)`：无法解析主体或资源。

`OwnedContext.require()` 用于业务代码读取已校验主体；未完成归属校验时调用会失败。内核只暴露中性的 `Object subject`，具体学生、员工、客户等类型由采用方封装。

## 2. 执行语义

| 场景 | shadow | enforce | reason |
|---|---|---|---|
| `@PublicAccess` | 放行 | 放行 | — |
| 匿名 | 401 | 401 | `owned-unauthenticated` |
| 客户端 token | 记录后放行 | 403 | `owned-not-user` |
| `Unresolved` | 记录后放行 | 401「无法确认本人身份」（已登录）；匿名仍「未登录」 | `owned-unresolved` |
| `NotOwner` | 记录后放行 | 403 | `owned-not-owner` |
| `Ok` | 绑定并放行 | 绑定并放行 | `owned-ok[:suffix]` |
| `sysAdmin` 且允许 bypass | 放行 | 放行 | `owned-ok:admin` |

`off` 模式不执行归属校验。shadow 不豁免匿名访问，只放过已认证主体的归属失败。

## 3. 注解与启动校验

- `@Authenticated(resolver=…)` 与 `@Permission` 互斥；一个方法只选择一个鉴权档位。
- 指定的 resolver 必须是容器中的 Bean。
- `param` 声明但请求中缺值时按 `Unresolved`。
- `@PublicAccess` 优先。
- 类级注解对全部处理方法生效。

内核在应用上下文完成后扫描 mapping；互斥注解或 resolver 缺失会导致启动失败，避免运行时静默绕过。

## 4. 配置

```yaml
platform:
  security:
    owned:
      enabled: true
      admin-bypass: true
```

没有 Owned 注解的项目即使开启该组件也不会改变普通 `@Authenticated` 和 `@Permission` 的行为。

## 5. 验收

- 登录-only 方法不由 Owned 拦截器处理。
- Ok 会绑定 subject 与 user；各种失败在 shadow/enforce 下得到正确状态码与 reason。
- 匿名在 shadow/enforce 下均为 401；客户端不能伪装成用户本人。
- query 与路径变量均可取参，缺参可识别。
- resolver 缺失、与 `@Permission` 同标时启动失败。
- `sysAdmin` bypass 可配置，关闭后正常进入 resolver。
