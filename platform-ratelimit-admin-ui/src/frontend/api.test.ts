import { describe, expect, it } from "vitest";
import {
  asOpaqueId,
  isLiveRefreshSeconds,
  LIVE_REFRESH_SECONDS,
  readStoredAuthorization,
  resolveApiBase,
  unwrap,
} from "./api";

describe("unwrap", () => {
  it("treats HTTP 200 business failure as error", () => {
    expect(() => unwrap(200, { code: -1, message: "保存失败" })).toThrow("保存失败");
  });

  it("merges 409 latest version without applying it", () => {
    try {
      unwrap(409, {
        code: -1,
        message: "策略不存在或已被修改，请刷新后重试",
        data: { id: "9", version: "4", policyCode: "auth.login" },
      });
      throw new Error("should throw");
    } catch (ex) {
      const err = ex as { latest?: { version?: string }; message: string };
      expect(err.message).toContain("已被修改");
      expect(err.latest?.version).toBe("4");
      expect(asOpaqueId(err.latest?.version)).toBe("4");
    }
  });
});

describe("readStoredAuthorization", () => {
  it("parses JSON-stringified bearer from sessionStorage", () => {
    sessionStorage.setItem("stormwind_Authorization", JSON.stringify("bearer abc"));
    expect(readStoredAuthorization("stormwind_Authorization")).toBe("bearer abc");
    sessionStorage.removeItem("stormwind_Authorization");
  });
});

describe("live refresh seconds", () => {
  it("accepts the console intervals", () => {
    expect(LIVE_REFRESH_SECONDS).toEqual([1, 5, 10, 30, 60]);
    expect(isLiveRefreshSeconds(5)).toBe(true);
    expect(isLiveRefreshSeconds(7)).toBe(false);
  });
});

describe("resolveApiBase", () => {
  it("uses host config instead of relative admin path", () => {
    expect(resolveApiBase({ apiBase: "/ctx/platform/admin/rate-limit/policies", contextPath: "/ctx", uiPath: "/ctx/ops" }, "../admin"))
      .toBe("/ctx/platform/admin/rate-limit/policies");
  });

  it("keeps prefix-relative apiBase for gateway and Vite proxy", () => {
    expect(resolveApiBase({
      apiBase: "../admin/rate-limit/policies",
      contextPath: "",
      uiPath: "/platform/ratelimit",
    }, "/platform/admin/rate-limit/policies")).toBe("../admin/rate-limit/policies");
  });
});
