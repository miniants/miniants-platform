export type Algorithm = "GCRA" | "SLIDING_WINDOW";
export type StoreFailure = "ALLOW" | "DENY";

export interface PolicyDraft {
  policyCode: string;
  algorithm: Algorithm;
  limitCount: number;
  periodMs: number;
  burst?: number;
  storeFailurePolicy: StoreFailure;
  enabled: boolean;
  remark?: string;
}

export function validatePolicyDraft(draft: PolicyDraft): string[] {
  const errors: string[] = [];
  if (!draft.policyCode || !draft.policyCode.trim()) {
    errors.push("策略编码不能为空");
  }
  if (draft.limitCount == null || draft.limitCount <= 0) {
    errors.push("限流阈值必须为正数");
  }
  if (draft.periodMs == null || draft.periodMs <= 0) {
    errors.push("限流周期必须为正数");
  }
  if (draft.algorithm === "GCRA" && draft.burst != null && draft.burst <= 0) {
    errors.push("突发容量必须为正数");
  }
  return errors;
}

export function summarizeImpact(draft: PolicyDraft): string {
  const windowSec = Math.max(1, Math.round(draft.periodMs / 1000));
  if (draft.algorithm === "SLIDING_WINDOW") {
    return `任意连续 ${windowSec} 秒最多 ${draft.limitCount} 次`;
  }
  const burst = draft.burst && draft.burst > 0 ? draft.burst : draft.limitCount;
  return `${windowSec} 秒 ${draft.limitCount} 次，突发 ${burst}`;
}
