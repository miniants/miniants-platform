package cn.miniants.platform.integration.concurrent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 在明确命名的有界线程池中执行批量工作，并保留每一项的执行结果。
 */
public class BoundedBatchExecutor implements AutoCloseable {

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private final ThreadPoolExecutor executor;
    private final String threadNamePrefix;
    private final RejectionPolicy rejectionPolicy;

    public BoundedBatchExecutor(
            String threadNamePrefix,
            int concurrency,
            int queueCapacity,
            RejectionPolicy rejectionPolicy) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("并发数必须大于 0");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("队列容量不能小于 0");
        }
        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "线程名前缀不能为空");
        this.rejectionPolicy = Objects.requireNonNull(rejectionPolicy, "拒绝策略不能为空");
        BlockingQueue<Runnable> queue = queueCapacity == 0
                ? new SynchronousQueue<>()
                : new ArrayBlockingQueue<>(queueCapacity);
        this.executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0,
                TimeUnit.MILLISECONDS,
                queue,
                namedThreadFactory(threadNamePrefix),
                rejectionPolicy.handler());
    }

    public <T> BatchResult<T> execute(Collection<T> items, ThrowingConsumer<T> action) {
        return execute(items, action, ignored -> {
        });
    }

    public <T> BatchResult<T> execute(
            Collection<T> items,
            ThrowingConsumer<T> action,
            Consumer<ItemResult<T>> progress) {
        Objects.requireNonNull(items, "批量数据不能为空");
        Objects.requireNonNull(action, "单项操作不能为空");
        Objects.requireNonNull(progress, "进度回调不能为空");

        List<CompletableFuture<ItemResult<T>>> futures = new ArrayList<>(items.size());
        int maxInFlight = executor.getCorePoolSize();
        int nextToAwait = 0;
        for (T item : items) {
            CompletableFuture<ItemResult<T>> future = new CompletableFuture<>();
            try {
                executor.execute(() -> completeItem(future, item, action, progress));
            } catch (RejectedExecutionException exception) {
                completeRejected(future, item, exception, progress);
            }
            futures.add(future);
            if (futures.size() - nextToAwait >= maxInFlight) {
                futures.get(nextToAwait).join();
                nextToAwait++;
            }
        }
        return new BatchResult<>(futures.stream().map(CompletableFuture::join).toList());
    }

    public int concurrency() {
        return executor.getCorePoolSize();
    }

    public int queueCapacity() {
        return executor.getQueue().remainingCapacity() + executor.getQueue().size();
    }

    public String threadNamePrefix() {
        return threadNamePrefix;
    }

    public RejectionPolicy rejectionPolicy() {
        return rejectionPolicy;
    }

    int queuedTaskCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static <T> void completeItem(
            CompletableFuture<ItemResult<T>> future,
            T item,
            ThrowingConsumer<T> action,
            Consumer<ItemResult<T>> progress) {
        ItemResult<T> result;
        try {
            action.accept(item);
            result = ItemResult.success(item);
        } catch (Exception exception) {
            result = ItemResult.failure(item, exception);
        }
        future.complete(withProgress(result, progress));
    }

    private static <T> void completeRejected(
            CompletableFuture<ItemResult<T>> future,
            T item,
            RejectedExecutionException exception,
            Consumer<ItemResult<T>> progress) {
        future.complete(withProgress(ItemResult.failure(item, exception), progress));
    }

    private static <T> ItemResult<T> withProgress(
            ItemResult<T> result,
            Consumer<ItemResult<T>> progress) {
        try {
            progress.accept(result);
            return result;
        } catch (RuntimeException progressException) {
            if (result.error() != null) {
                result.error().addSuppressed(progressException);
                return result;
            }
            return ItemResult.failure(result.item(), progressException);
        }
    }

    private static ThreadFactory namedThreadFactory(String threadNamePrefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task);
            thread.setName(threadNamePrefix + sequence.incrementAndGet());
            return thread;
        };
    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> {
        void accept(T item) throws Exception;
    }

    public record ItemResult<T>(T item, Throwable error) {

        public static <T> ItemResult<T> success(T item) {
            return new ItemResult<>(item, null);
        }

        public static <T> ItemResult<T> failure(T item, Throwable error) {
            return new ItemResult<>(item, Objects.requireNonNull(error));
        }

        public boolean succeeded() {
            return error == null;
        }
    }

    public record BatchResult<T>(List<ItemResult<T>> items) {

        public BatchResult {
            items = List.copyOf(items);
        }

        public long succeededCount() {
            return items.stream().filter(ItemResult::succeeded).count();
        }

        public long failedCount() {
            return items.size() - succeededCount();
        }

        public List<ItemResult<T>> failures() {
            return items.stream().filter(item -> !item.succeeded()).toList();
        }

        public boolean hasFailures() {
            return failedCount() > 0;
        }
    }
}
