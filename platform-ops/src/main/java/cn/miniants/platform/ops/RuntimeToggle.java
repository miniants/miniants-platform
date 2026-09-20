package cn.miniants.platform.ops;

/**
 * 可在运行时开停的后台组件，不改配置、不重启进程。
 *
 * <p>典型场景：把某台机器的定时任务执行器摘掉再排查，或者发布前先停掉消费者。
 */
public interface RuntimeToggle extends RuntimeStateContributor {

    void setRuntimeEnabled(boolean enabled);
}
