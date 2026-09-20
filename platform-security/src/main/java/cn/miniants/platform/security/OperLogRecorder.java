package cn.miniants.platform.security;

/**
 * 操作日志落库。admin 默认写 {@code sys_oper_log}；没有 Bean 则不记。
 */
@FunctionalInterface
public interface OperLogRecorder {

    void record(OperLogEntry entry);
}
