---
title: AutoConfiguration 规范
status: active
owner: platform
last_verified: 2026-08-27
---

# AutoConfiguration 规范

平台横切能力使用 Spring Boot AutoConfiguration 直接装配，不要求采用方在启动类上增加平台门面注解。

## 1. 注册方式

- 自动装配类使用 `@AutoConfiguration`，类名以 `AutoConfiguration` 结尾。
- 写入 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 不通过 `@SpringBootApplication` 派生注解 `@Import` 横切配置。
- 不增加只负责转发 `@Import` 的空壳配置类。

## 2. 条件与顺序

- 使用 `@ConditionalOnClass`、`@ConditionalOnWebApplication`、`@ConditionalOnProperty` 和 `@ConditionalOnMissingBean` 表达能力边界。
- 安全能力缺省关闭或使用安全默认值；没有依赖类时不注册 Bean。
- 条件不能代替装配顺序。两个配置都使用 MissingBean，或一个配置在注册期读取另一个配置提供的 Bean 时，必须声明 `before`、`after`、`beforeName` 或 `afterName`。
- 排序注释应说明排反后的具体故障，例如静默退化成本地实现或选择错误序列化器。
- 依赖后续 AutoConfiguration 创建的 Bean 时，优先在 Bean 方法参数或 `ObjectProvider` 中延迟获取，不在注册期用 `@ConditionalOnBean` 误判。
- `@Lazy` 不改变 Bean 定义注册顺序。

## 3. 可替换性

- SPI 和默认实现使用 `@ConditionalOnMissingBean`，允许采用方覆盖。
- 自动装配应按能力拆分，避免采用方为替换一个 Bean 而排除整组模块。
- 每个属性有明确默认值；密钥、口令、Redis 稳定前缀等不能提供危险默认值，必要时缺失即拒绝启动。
- 可选依赖使用 `compileOnly` 或条件类装配，不能把未使用的厂商实现拖入所有应用。
- 可选类型（如 `StringRedisTemplate`）不要写在带 `@ConditionalOnMissingBean` 的主配置类方法签名里。Boot 4 评估该条件会内省本类全部方法，缺类时整份配置起不来。把 Redis 相关 Bean 放到 `@ConditionalOnClass` 的嵌套 `@Configuration`。

## 4. 验收

新增或修改 AutoConfiguration 至少验证：

1. 满足条件时 Bean 生效。
2. 缺依赖、关闭属性或缺配置时不注册。
3. 采用方提供替代 Bean 时默认实现退让。
4. 有顺序竞争时最终选择预期实现。
5. `AutoConfiguration.imports` 包含应发布的配置，聚合 starter 的模块清单无遗漏。

应用只使用标准 `@SpringBootApplication` 即可获得 classpath 上的已启用平台能力。
