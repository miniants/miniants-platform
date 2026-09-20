import { describe, expect, it } from "vitest";
import { summarizeImpact, validatePolicyDraft } from "./policyForm";

describe("validatePolicyDraft", () => {
  it("rejects empty code and non-positive numbers", () => {
    expect(
      validatePolicyDraft({
        policyCode: " ",
        algorithm: "GCRA",
        limitCount: 0,
        periodMs: -1,
        storeFailurePolicy: "DENY",
        enabled: true,
      }),
    ).toEqual(["策略编码不能为空", "限流阈值必须为正数", "限流周期必须为正数"]);
  });

  it("accepts sliding window without burst", () => {
    expect(
      validatePolicyDraft({
        policyCode: "auth.login",
        algorithm: "SLIDING_WINDOW",
        limitCount: 3,
        periodMs: 600_000,
        storeFailurePolicy: "DENY",
        enabled: true,
      }),
    ).toEqual([]);
  });
});

describe("summarizeImpact", () => {
  it("describes GCRA burst", () => {
    expect(
      summarizeImpact({
        policyCode: "auth.login",
        algorithm: "GCRA",
        limitCount: 20,
        periodMs: 60_000,
        burst: 40,
        storeFailurePolicy: "DENY",
        enabled: true,
      }),
    ).toBe("60 秒 20 次，突发 40");
  });

  it("describes sliding window", () => {
    expect(
      summarizeImpact({
        policyCode: "sms",
        algorithm: "SLIDING_WINDOW",
        limitCount: 3,
        periodMs: 600_000,
        storeFailurePolicy: "DENY",
        enabled: true,
      }),
    ).toContain("最多 3 次");
  });
});
