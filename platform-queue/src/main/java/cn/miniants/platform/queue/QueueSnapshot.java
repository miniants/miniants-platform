package cn.miniants.platform.queue;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link QueueMode#JOBS} 管理端快照。计数按二级索引过滤后的整队（不按 status）；
 * {@link #total} / {@link #jobs} 再按 status 过滤，{@link #jobs} 是当前页。
 */
public final class QueueSnapshot {

    private long queued;
    private long delayed;
    private long processing;
    private long succeeded;
    private long failed;
    private long total;
    private List<QueueJob> jobs = new ArrayList<>();

    public long getQueued() {
        return queued;
    }

    public void setQueued(long queued) {
        this.queued = queued;
    }

    public long getDelayed() {
        return delayed;
    }

    public void setDelayed(long delayed) {
        this.delayed = delayed;
    }

    public long getProcessing() {
        return processing;
    }

    public void setProcessing(long processing) {
        this.processing = processing;
    }

    public long getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(long succeeded) {
        this.succeeded = succeeded;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<QueueJob> getJobs() {
        return jobs;
    }

    public void setJobs(List<QueueJob> jobs) {
        this.jobs = jobs == null ? new ArrayList<>() : jobs;
    }
}
