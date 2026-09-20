---
title: Redis 连续队列
status: active
owner: platform
last_verified: 2026-08-27
---

# Redis 连续队列

`platform-queue` 提供可持续消费、延迟唤醒、重试、排空和孤儿回收能力。场景显式构建队列并提供处理器；组件不理解业务 payload、权限或 HTTP DTO。

## 1. 组件边界

```text
QueueKeyFactory         Redis 键
QueueSpec / builder     名称、模式、并发、TTL、索引
QueueRouting            ready 路由
QueueHandler            process(jobId)
ContinuousQueue         入队、延迟、成功失败、锁、快照、回收
ContinuousQueueWorker   BRPOP、闹钟、孤儿回收、drain
```

一条队列对应一次显式构建和一个 `ContinuousQueueWorker` Bean。场景不要继承 Worker，也不要直接操作内部 Hash、ZSet 或锁。

`QueueKeyFactory` 必须由采用方注入。自动装配可以从稳定的 `platform.queue.key-prefix` 创建前缀工厂，但不提供默认前缀：前缀写错等于切换到一套新队列，已有任务会不可见。

## 2. 两种模式

| 模式 | 用途 | Redis 结构 |
|---|---|---|
| `JOBS` | 可观察、可重试、可手动重入队的任务 | ready、delay、job Hash、lock、index |
| `ALARM` | 到点触发，member 本身就是业务 ID | ready、delay、wake，无 job Hash |

业务级互斥、外部连接状态、payload 字段和管理权限留在场景实现。

## 3. 消费与闹钟

- 工人通过 `BRPOP` 消费 routing 返回的 ready 键。
- 阻塞时长必须短于 Redis 客户端命令超时，避免空队列被误判成查询超时。
- 闹钟只查看 delay 中最早一条，未到期则 park；到期通过 Lua 原子执行 `ZREM + LPUSH`。
- 更早的新任务写入 delay 后必须发布 wake，唤醒重新计算等待时间。
- 每个运行工人的应用实例只启一条该队列的闹钟线程。
- wake channel 与缓存失效 channel 分离。

等待时间到期重试使用 `delay`；等待外部事件使用 `park`，由事件到来时 `readyNow` 唤醒。

## 4. 工人生命周期

`ContinuousQueueWorker` 实现 `RuntimeStateContributor`，并可注入 `RuntimeDrain`：

- drain 后停止领取新任务，允许在途任务收尾。
- 实例异常退出留下的锁或工作态由孤儿回收处理。
- 并发数由场景提供；处理器负责把业务异常映射为重试、停等或终态失败。
- JOBS 处理锁由调用方为每次持有生成唯一 owner token；释放时必须同时提交该 token，
  组件通过 Lua 原子比较 `GET` 后再 `DEL`。租约过期后的旧 owner 不得释放新 owner 的锁。

## 5. 观察与操作

`snapshot(status, index, offset, limit)` 返回任务视图。二级索引用于场景字段筛选；状态过滤在索引结果上继续执行。日志只记录任务 ID、状态迁移和错误摘要，不记录密钥或完整敏感 payload。

管理端手工 `requeue` 只允许处理当前无有效锁的任务。它不主动删除锁：锁已过期时无需清理，
无锁检查后若恰有新 owner 获锁，也不会误删新租约。

## 6. 验收

- JOBS 与 ALARM 的入队、延迟推进和消费路径正确。
- Lua 推进不会重复投递；更早任务会唤醒闹钟。
- BRPOP 阻塞时间小于客户端超时，空队列不持续刷 WARN。
- drain 后不领新任务，在途任务可完成。
- 锁过期、实例退出和孤儿回收有覆盖；旧 owner 条件释放不会删除续租后的新 owner 锁。
- 前缀缺失时不注册默认 `QueueKeyFactory`；不同前缀不会静默混用。
