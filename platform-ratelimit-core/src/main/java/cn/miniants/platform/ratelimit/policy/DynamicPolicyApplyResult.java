package cn.miniants.platform.ratelimit.policy;

/**
 * 动态层 revision CAS 结果。
 */
public enum DynamicPolicyApplyResult {
    APPLIED,
    TOUCHED,
    REJECTED_STALE
}
