package cn.miniants.platform.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/**
 * 一条 Redis 连续队列实例：就绪 List + delay 闹钟，{@link QueueMode#JOBS} 另含 Hash / 锁 / 索引。
 */
public final class ContinuousQueue implements QueueBackend {

    private static final Logger log = LoggerFactory.getLogger(ContinuousQueue.class);

    private static final int SNAPSHOT_DEFAULT_LIMIT = 200;
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(60);
    private static final Duration DEFAULT_QUEUED_STALE = Duration.ofSeconds(180);
    private static final Duration DEFAULT_ACTIVE_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_TERMINAL_TTL = Duration.ofHours(24);
    private static final Duration DEFAULT_FAILED_TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;
    private final QueueKeyFactory keys;
    private final QueueSpec spec;
    private final QueueRouting routing;
    private final QueueHandler handler;
    private final QueueWorkerSupport lifecycle;
    private final RedisBlockingPoller poller;
    private final RedisDelayZSet delayZSet;
    private final RedisOwnerLock locks;
    private final String wakeChannel;

    private ContinuousQueue(StringRedisTemplate redis, QueueKeyFactory keys, QueueSpec spec,
            QueueRouting routing, QueueHandler handler) {
        this.redis = redis;
        this.keys = keys;
        this.spec = spec;
        this.routing = routing;
        this.handler = handler;
        this.lifecycle = new QueueWorkerSupport();
        this.poller = new RedisBlockingPoller(redis, lifecycle, spec.logLabel());
        this.wakeChannel = keys.wake(spec.name());
        this.delayZSet = new RedisDelayZSet(redis, keys.delay(spec.name()), routing.promoteReadyKey());
        this.locks = new RedisOwnerLock(redis);
    }

    public static Builder builder(StringRedisTemplate redis, String name, QueueKeyFactory keys) {
        return new Builder(redis, name, keys);
    }

    public QueueSpec spec() {
        return spec;
    }

    public String name() {
        return spec.name();
    }

    public String wakeChannel() {
        return wakeChannel;
    }

    QueueWorkerSupport lifecycle() {
        return lifecycle;
    }

    public QueueJob getJob(String jobId) {
        requireJobs();
        Map<String, String> fields = readHash(jobId);
        if (fields.isEmpty()) {
            return null;
        }
        return new QueueJob(jobId, fields);
    }

    public Set<String> indexedIds(String indexValue) {
        requireJobs();
        if (!StringUtils.hasText(spec.indexField()) || !StringUtils.hasText(indexValue)) {
            return Set.of();
        }
        Set<String> ids = redis.opsForSet().members(indexKey(indexValue));
        return ids == null ? Set.of() : ids;
    }

    public String enqueue(Map<String, String> payload) {
        requireJobs();
        if (payload == null) {
            throw new IllegalArgumentException("任务内容不能为空");
        }
        String jobId = StringUtils.hasText(payload.get(QueueJobFields.JOB_ID))
                ? payload.get(QueueJobFields.JOB_ID).trim()
                : newJobId();
        String now = QueueTimeFormats.nowIso();
        Map<String, String> job = new LinkedHashMap<>(payload);
        job.put(QueueJobFields.JOB_ID, jobId);
        job.put(QueueJobFields.STATUS, QueueJobStatus.QUEUED);
        job.put(QueueJobFields.ATTEMPT, "0");
        job.put(QueueJobFields.MAX_ATTEMPTS, String.valueOf(spec.maxAttempts()));
        job.put(QueueJobFields.LAST_ERROR, job.getOrDefault(QueueJobFields.LAST_ERROR, ""));
        job.put(QueueJobFields.ENQUEUED_AT, now);
        job.put(QueueJobFields.UPDATED_AT, now);
        job.put(QueueJobFields.LOCKED_BY, "");
        redis.opsForHash().putAll(jobKey(jobId), job);
        redis.opsForSet().add(indexKey(), jobId);
        redis.expire(jobKey(jobId), spec.activeTtl());
        touchIndexField(jobId, job, true);
        pushReady(jobId, job);
        log.info("{} 已入队 jobId={}", spec.logLabel(), jobId);
        return jobId;
    }

    /**
     * {@link QueueMode#ALARM}：只登记 delay member。{@link QueueMode#JOBS}：已有任务改 DELAYED 并 ZADD。
     */
    public void schedule(String jobId, long epochMs) {
        if (!StringUtils.hasText(jobId)) {
            return;
        }
        if (spec.mode() == QueueMode.JOBS) {
            QueueJob existing = getJob(jobId);
            if (existing == null) {
                throw new IllegalStateException("队列中找不到该任务");
            }
            patch(jobId, Map.of(
                    QueueJobFields.STATUS, QueueJobStatus.DELAYED,
                    QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso(),
                    QueueJobFields.LOCKED_BY, ""));
        }
        delayZSet.zadd(jobId, epochMs);
        delayZSet.publishWake(wakeChannel);
    }

    public void cancel(String jobId) {
        if (StringUtils.hasText(jobId)) {
            delayZSet.remove(jobId);
        }
    }

    public void delay(String jobId, String error, long seconds) {
        requireJobs();
        long when = System.currentTimeMillis() + Math.max(1L, seconds) * 1000L;
        patch(jobId, Map.of(
                QueueJobFields.STATUS, QueueJobStatus.DELAYED,
                QueueJobFields.LAST_ERROR, error == null ? "" : error,
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso(),
                QueueJobFields.LOCKED_BY, ""));
        delayZSet.zadd(jobId, when);
        delayZSet.publishWake(wakeChannel);
        log.info("{} 延迟 jobId={} after={}s err={}", spec.logLabel(), jobId, seconds, error);
    }

    public void delayWithBackoff(String jobId, String error) {
        QueueJob job = getJob(jobId);
        int attempt = job == null ? 1 : Math.max(1, job.attempt());
        delay(jobId, error, RetryBackoff.secondsAfterAttempt(attempt));
    }

    /**
     * 停在 DELAYED，不进闹钟。等 {@link #readyNow}（如设备重连），不要用 delay 秒数轮询在线。
     */
    public void park(String jobId, String error) {
        requireJobs();
        delayZSet.remove(jobId);
        patch(jobId, Map.of(
                QueueJobFields.STATUS, QueueJobStatus.DELAYED,
                QueueJobFields.LAST_ERROR, error == null ? "" : error,
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso(),
                QueueJobFields.LOCKED_BY, ""));
        redis.expire(jobKey(jobId), spec.activeTtl());
        QueueJob parked = getJob(jobId);
        String indexVal = parked == null || !StringUtils.hasText(spec.indexField())
                ? ""
                : parked.get(spec.indexField());
        if (StringUtils.hasText(indexVal)) {
            log.info("{} 停等事件 {}={} jobId={} err={}", spec.logLabel(), spec.indexField(), indexVal, jobId, error);
        } else {
            log.info("{} 停等事件 jobId={} err={}", spec.logLabel(), jobId, error);
        }
    }

    public void readyNow(String jobId) {
        requireJobs();
        delayZSet.remove(jobId);
        QueueJob job = getJob(jobId);
        if (job == null) {
            return;
        }
        patch(jobId, Map.of(
                QueueJobFields.STATUS, QueueJobStatus.QUEUED,
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso()));
        pushReady(jobId, job.fields());
    }

    public void pushTo(String jobId, String readyKey) {
        if (StringUtils.hasText(jobId) && StringUtils.hasText(readyKey)) {
            redis.opsForList().leftPush(readyKey, jobId);
        }
    }

    /**
     * ownerToken 必须由调用方为每次持锁生成，不能在两次租约间复用。
     */
    public boolean tryLock(String jobId, String ownerToken) {
        requireJobs();
        return locks.tryAcquire(lockKey(jobId), ownerToken, spec.lockTtl());
    }

    public boolean unlock(String jobId, String ownerToken) {
        requireJobs();
        return locks.release(lockKey(jobId), ownerToken);
    }

    public boolean isLocked(String jobId) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(jobId)));
    }

    public QueueJob markProcessing(String jobId, String owner) {
        requireJobs();
        QueueJob job = getJob(jobId);
        int attempt = (job == null ? 0 : job.attempt()) + 1;
        patch(jobId, Map.of(
                QueueJobFields.STATUS, QueueJobStatus.PROCESSING,
                QueueJobFields.ATTEMPT, String.valueOf(attempt),
                QueueJobFields.LOCKED_BY, owner == null ? "" : owner,
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso()));
        return getJob(jobId);
    }

    public void succeed(String jobId) {
        requireJobs();
        finish(jobId, QueueJobStatus.SUCCEEDED, "", spec.terminalTtl());
    }

    public void fail(String jobId, String error) {
        requireJobs();
        finish(jobId, QueueJobStatus.FAILED, error, spec.failedTtl());
        log.warn("{} 失败 jobId={} err={}", spec.logLabel(), jobId, error);
    }

    public void requeue(String jobId) {
        requireJobs();
        QueueJob job = getJob(jobId);
        if (job == null) {
            throw new IllegalStateException("队列中找不到该任务");
        }
        if (QueueJobStatus.SUCCEEDED.equals(job.status())) {
            throw new IllegalStateException("任务已成功，无需重试");
        }
        if (QueueJobStatus.PROCESSING.equals(job.status()) && isLocked(jobId)) {
            throw new IllegalStateException("任务正在处理中，请稍后再试");
        }
        delayZSet.remove(jobId);
        patch(jobId, Map.of(
                QueueJobFields.STATUS, QueueJobStatus.QUEUED,
                QueueJobFields.ATTEMPT, "0",
                QueueJobFields.LAST_ERROR, "管理端手动重新入队",
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso(),
                QueueJobFields.LOCKED_BY, ""));
        touchIndexField(jobId, job.fields(), true);
        pushReady(jobId, job.fields());
    }

    public QueueSnapshot snapshot(String statusFilter, String indexValue, int limit) {
        return snapshot(statusFilter, indexValue, 0, limit);
    }

    public QueueSnapshot snapshot(String statusFilter, String indexValue, int offset, int limit) {
        requireJobs();
        QueueSnapshot snapshot = new QueueSnapshot();
        Set<String> ids = redis.opsForSet().members(indexKey());
        if (ids == null) {
            return snapshot;
        }
        List<QueueJob> jobs = new ArrayList<>();
        for (String jobId : ids) {
            QueueJob job = getJob(jobId);
            if (job == null) {
                continue;
            }
            boolean indexOk = !StringUtils.hasText(indexValue)
                    || !StringUtils.hasText(spec.indexField())
                    || indexValue.equals(job.get(spec.indexField()));
            if (!indexOk) {
                continue;
            }
            switch (job.status()) {
                case QueueJobStatus.QUEUED -> snapshot.setQueued(snapshot.getQueued() + 1);
                case QueueJobStatus.DELAYED -> snapshot.setDelayed(snapshot.getDelayed() + 1);
                case QueueJobStatus.PROCESSING -> snapshot.setProcessing(snapshot.getProcessing() + 1);
                case QueueJobStatus.SUCCEEDED -> snapshot.setSucceeded(snapshot.getSucceeded() + 1);
                case QueueJobStatus.FAILED -> snapshot.setFailed(snapshot.getFailed() + 1);
                default -> {
                }
            }
            boolean statusOk = !StringUtils.hasText(statusFilter) || statusFilter.equals(job.status());
            if (statusOk) {
                jobs.add(job);
            }
        }
        jobs.sort((a, b) -> b.updatedAt().compareTo(a.updatedAt()));
        snapshot.setTotal(jobs.size());
        int from = Math.max(offset, 0);
        int cap = limit > 0 ? limit : SNAPSHOT_DEFAULT_LIMIT;
        if (from >= jobs.size()) {
            snapshot.setJobs(List.of());
            return snapshot;
        }
        int to = Math.min(from + cap, jobs.size());
        snapshot.setJobs(new ArrayList<>(jobs.subList(from, to)));
        return snapshot;
    }

    @Override
    public void beginShutdown() {
        lifecycle.beginShutdown();
    }

    @Override
    public boolean shouldStopPolling() {
        return lifecycle.shouldStopPolling();
    }

    @Override
    public String pollReady(long timeout, TimeUnit unit) {
        return poller.pollReady(routing.pollKeys(), timeout, unit);
    }

    @Override
    public boolean promoteFirstDue() {
        return delayZSet.promoteFirstDue(System.currentTimeMillis());
    }

    @Override
    public OptionalLong peekDelayScore() {
        return delayZSet.peekScore();
    }

    @Override
    public void reclaimOrphanJobs() {
        if (spec.mode() != QueueMode.JOBS) {
            return;
        }
        Set<String> ids = redis.opsForSet().members(indexKey());
        if (ids == null) {
            return;
        }
        Instant now = Instant.now();
        long staleSeconds = spec.queuedStale().toSeconds();
        for (String jobId : ids) {
            QueueJob job = getJob(jobId);
            if (job == null) {
                redis.opsForSet().remove(indexKey(), jobId);
                continue;
            }
            if (QueueJobStatus.PROCESSING.equals(job.status()) && !isLocked(jobId)) {
                patch(jobId, Map.of(
                        QueueJobFields.STATUS, QueueJobStatus.QUEUED,
                        QueueJobFields.LOCKED_BY, "",
                        QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso()));
                pushReady(jobId, job.fields());
            } else if (QueueJobStatus.QUEUED.equals(job.status())
                    && QueueTimeFormats.isOlderThan(job.updatedAt(), staleSeconds, now)) {
                patch(jobId, Map.of(QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso()));
                pushReady(jobId, job.fields());
            }
        }
    }

    @Override
    public void processJob(String jobId) {
        if (spec.mode() == QueueMode.JOBS) {
            QueueJob job = getJob(jobId);
            if (job == null || job.isTerminal()) {
                return;
            }
        }
        handler.process(jobId);
    }

    private void finish(String jobId, String status, String error, Duration ttl) {
        QueueJob job = getJob(jobId);
        delayZSet.remove(jobId);
        patch(jobId, Map.of(
                QueueJobFields.STATUS, status,
                QueueJobFields.LAST_ERROR, error == null ? "" : error,
                QueueJobFields.UPDATED_AT, QueueTimeFormats.nowIso(),
                QueueJobFields.LOCKED_BY, ""));
        redis.expire(jobKey(jobId), ttl);
        if (job != null) {
            touchIndexField(jobId, job.fields(), false);
        }
    }

    private void pushReady(String jobId, Map<String, String> job) {
        String ready = routing.pushKey(job == null ? Map.of() : job);
        redis.opsForList().leftPush(ready, jobId);
    }

    private void patch(String jobId, Map<String, String> fields) {
        redis.opsForHash().putAll(jobKey(jobId), fields);
    }

    private Map<String, String> readHash(String jobId) {
        Map<Object, Object> raw = redis.opsForHash().entries(jobKey(jobId));
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
        }
        return out;
    }

    private void touchIndexField(String jobId, Map<String, String> job, boolean add) {
        if (!StringUtils.hasText(spec.indexField()) || job == null) {
            return;
        }
        String value = job.get(spec.indexField());
        if (!StringUtils.hasText(value)) {
            return;
        }
        String key = indexKey(value);
        if (add) {
            redis.opsForSet().add(key, jobId);
        } else {
            redis.opsForSet().remove(key, jobId);
        }
    }

    private void requireJobs() {
        if (spec.mode() != QueueMode.JOBS) {
            throw new IllegalStateException("ALARM 队列没有任务 Hash: " + spec.name());
        }
    }

    private String jobKey(String jobId) {
        return keys.job(spec.name(), jobId);
    }

    private String lockKey(String jobId) {
        return keys.lock(spec.name(), jobId);
    }

    private String indexKey() {
        return keys.index(spec.name());
    }

    private String indexKey(String value) {
        return keys.indexBy(spec.name(), spec.indexField(), value);
    }

    private static String newJobId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static final class Builder {

        private final StringRedisTemplate redis;
        private final String name;
        private final QueueKeyFactory keys;
        private String logLabel;
        private QueueMode mode = QueueMode.JOBS;
        private int maxAttempts = 10;
        private Duration lockTtl = DEFAULT_LOCK_TTL;
        private Duration queuedStale = DEFAULT_QUEUED_STALE;
        private Duration activeTtl = DEFAULT_ACTIVE_TTL;
        private Duration terminalTtl = DEFAULT_TERMINAL_TTL;
        private Duration failedTtl = DEFAULT_FAILED_TTL;
        private String indexField;
        private IntSupplier concurrency = () -> 1;
        private QueueRouting routing;
        private QueueHandler handler;

        private Builder(StringRedisTemplate redis, String name, QueueKeyFactory keys) {
            if (redis == null) {
                throw new IllegalArgumentException("Redis 不能为空");
            }
            if (keys == null) {
                throw new IllegalArgumentException("QueueKeyFactory 不能为空");
            }
            QueueSpec.requireName(name);
            this.redis = redis;
            this.name = name;
            this.keys = keys;
            this.logLabel = name;
        }

        public Builder logLabel(String logLabel) {
            if (StringUtils.hasText(logLabel)) {
                this.logLabel = logLabel;
            }
            return this;
        }

        public Builder jobs() {
            this.mode = QueueMode.JOBS;
            return this;
        }

        public Builder alarms() {
            this.mode = QueueMode.ALARM;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = Math.max(1, maxAttempts);
            return this;
        }

        public Builder lockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
            return this;
        }

        public Builder queuedStale(Duration queuedStale) {
            this.queuedStale = queuedStale;
            return this;
        }

        public Builder activeTtl(Duration activeTtl) {
            this.activeTtl = activeTtl;
            return this;
        }

        public Builder terminalTtl(Duration terminalTtl) {
            this.terminalTtl = terminalTtl;
            return this;
        }

        public Builder failedTtl(Duration failedTtl) {
            this.failedTtl = failedTtl;
            return this;
        }

        public Builder indexField(String indexField) {
            this.indexField = indexField;
            return this;
        }

        public Builder concurrency(IntSupplier concurrency) {
            this.concurrency = concurrency == null ? () -> 1 : concurrency;
            return this;
        }

        public Builder routing(QueueRouting routing) {
            this.routing = routing;
            return this;
        }

        public Builder handler(QueueHandler handler) {
            this.handler = handler;
            return this;
        }

        public ContinuousQueue build() {
            if (handler == null) {
                throw new IllegalArgumentException("必须提供 QueueHandler");
            }
            if (mode == QueueMode.ALARM && StringUtils.hasText(indexField)) {
                throw new IllegalArgumentException("ALARM 队列不能设二级索引");
            }
            QueueRouting resolved = routing != null
                    ? routing
                    : QueueRouting.single(keys.ready(name));
            QueueSpec spec = new QueueSpec(name, logLabel, mode, maxAttempts, lockTtl, queuedStale,
                    activeTtl, terminalTtl, failedTtl, indexField, concurrency);
            return new ContinuousQueue(redis, keys, spec, resolved, handler);
        }
    }
}
