package cn.miniants.platform.integration.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedBatchExecutorTest {

    @Test
    void executesOnNamedThreadsAndAggregatesEveryError() {
        try (BoundedBatchExecutor executor =
                     new BoundedBatchExecutor("test-batch-", 2, 4, RejectionPolicy.ABORT)) {
            AtomicReference<String> threadName = new AtomicReference<>();

            BoundedBatchExecutor.BatchResult<Integer> result = executor.execute(List.of(1, 2, 3), item -> {
                threadName.compareAndSet(null, Thread.currentThread().getName());
                if (item == 2) {
                    throw new IllegalStateException("expected");
                }
            });

            assertThat(threadName.get()).startsWith("test-batch-");
            assertThat(result.succeededCount()).isEqualTo(2);
            assertThat(result.failedCount()).isOne();
            assertThat(result.failures().getFirst().item()).isEqualTo(2);
            assertThat(result.failures().getFirst().error()).hasMessage("expected");
        }
    }

    @Test
    void oneLargeBatchDoesNotRejectItself() {
        try (BoundedBatchExecutor executor =
                     new BoundedBatchExecutor("windowed-batch-", 1, 1, RejectionPolicy.ABORT)) {
            BoundedBatchExecutor.BatchResult<Integer> result =
                    executor.execute(List.of(1, 2, 3, 4, 5), item -> {
                    });

            assertThat(result.hasFailures()).isFalse();
        }
    }

    @Test
    void abortPolicyReportsSaturationAsItemFailure() throws Exception {
        try (BoundedBatchExecutor executor =
                     new BoundedBatchExecutor("abort-batch-", 1, 1, RejectionPolicy.ABORT)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            CompletableFuture<BoundedBatchExecutor.BatchResult<Integer>> occupying =
                    CompletableFuture.supplyAsync(() -> executor.execute(List.of(0), item -> {
                        firstStarted.countDown();
                        releaseFirst.await(5, TimeUnit.SECONDS);
                    }));

            assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<BoundedBatchExecutor.BatchResult<Integer>> queued =
                    CompletableFuture.supplyAsync(() -> executor.execute(List.of(1), item -> {
                    }));
            awaitQueuedTask(executor);
            BoundedBatchExecutor.BatchResult<Integer> result = executor.execute(List.of(2), item -> {
            });
            releaseFirst.countDown();
            assertThat(occupying.get(5, TimeUnit.SECONDS).hasFailures()).isFalse();
            assertThat(queued.get(5, TimeUnit.SECONDS).hasFailures()).isFalse();

            assertThat(result.failedCount()).isOne();
            assertThat(result.failures().getFirst().item()).isEqualTo(2);
            assertThat(result.failures().getFirst().error()).isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Test
    void callerRunsPolicyAppliesBackpressureOnSubmittingThread() {
        try (BoundedBatchExecutor executor =
                     new BoundedBatchExecutor("caller-runs-", 1, 0, RejectionPolicy.CALLER_RUNS)) {
            CountDownLatch workerStarted = new CountDownLatch(1);
            CountDownLatch releaseWorker = new CountDownLatch(1);
            AtomicReference<String> secondThread = new AtomicReference<>();
            String submittingThread = Thread.currentThread().getName();

            CompletableFuture<BoundedBatchExecutor.BatchResult<Integer>> occupying =
                    CompletableFuture.supplyAsync(() -> executor.execute(List.of(1), item -> {
                        workerStarted.countDown();
                        releaseWorker.await(5, TimeUnit.SECONDS);
                    }));
            try {
                assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            BoundedBatchExecutor.BatchResult<Integer> result = executor.execute(List.of(2), item -> {
                secondThread.set(Thread.currentThread().getName());
            });
            releaseWorker.countDown();
            assertThat(occupying.join().hasFailures()).isFalse();

            assertThat(result.hasFailures()).isFalse();
            assertThat(secondThread.get()).isEqualTo(submittingThread);
        }
    }

    private static void awaitQueuedTask(BoundedBatchExecutor executor) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.queuedTaskCount() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(executor.queuedTaskCount()).isOne();
    }
}
