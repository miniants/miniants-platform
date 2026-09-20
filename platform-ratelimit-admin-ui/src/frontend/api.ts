export interface ApiEnvelope<T> {
  code?: number | string;
  message?: string;
  data?: T;
}

export interface PolicyVo {
  id?: string;
  version?: string;
  policyCode: string;
  algorithm: "GCRA" | "SLIDING_WINDOW";
  limitCount: number;
  periodMs: number;
  burst?: number;
  storeFailurePolicy: "ALLOW" | "DENY";
  enabled: boolean;
  remark?: string;
}

export interface RevisionVo {
  revision: string;
  createdAt?: string;
  createdBy?: string;
  snapshotJson?: string;
}

export interface PageResult {
  records: PolicyVo[];
  total: number;
}

export type PolicySource = "yaml" | "dynamic" | "builtin";

export interface EffectivePolicyVo {
  policyCode: string;
  algorithm: PolicyVo["algorithm"];
  limitCount: number;
  periodMs: number;
  burst?: number;
  storeFailurePolicy: PolicyVo["storeFailurePolicy"];
  enabled: boolean;
  source?: PolicySource;
}

export interface RuntimeVo {
  revision: string;
  snapshotAgeMs?: number;
  snapshotStale?: boolean;
  sourceUnavailable?: boolean;
  publishFailed?: boolean;
  backend?: string;
  lastSuccessfulRefreshAt?: string;
  snapshotLoadedAt?: string;
  availablePolicies?: string[];
  effectivePolicies?: EffectivePolicyVo[];
}

export interface BucketVo {
  policyCode: string;
  subject: string;
  algorithm: PolicyVo["algorithm"] | string;
  limitCount: number;
  remaining: number;
  retryAfterMs: number;
  resetAtMs?: number;
}

export interface BucketsVo {
  observedAt?: string;
  backend?: string;
  truncated?: boolean;
  buckets?: BucketVo[];
}

export const LIVE_REFRESH_SECONDS = [1, 5, 10, 30, 60] as const;

export type LiveRefreshSeconds = (typeof LIVE_REFRESH_SECONDS)[number];

export function isLiveRefreshSeconds(value: number): value is LiveRefreshSeconds {
  return (LIVE_REFRESH_SECONDS as readonly number[]).includes(value);
}

export interface UiConfig {
  apiBase: string;
  contextPath: string;
  uiPath: string;
  authorizationStorageKey?: string;
}

export interface RequestError extends Error {
  status?: number;
  latest?: PolicyVo;
}

export function resolveApiBase(config: UiConfig | null, fallback: string): string {
  if (config?.apiBase) {
    return config.apiBase;
  }
  return fallback;
}

export function unwrap<T>(status: number, body: ApiEnvelope<T>): T {
  if (status === 401 || status === 403) {
    throw Object.assign(new Error("没有权限或登录已过期"), { status });
  }
  if (status === 409) {
    const latest = body.data as PolicyVo | undefined;
    throw Object.assign(new Error(body.message ?? "版本冲突，请刷新后重试"), {
      status,
      latest,
    });
  }
  if (Number(body.code) !== 200) {
    throw Object.assign(new Error(body.message ?? "请求失败"), { status });
  }
  return (body.data ?? body) as T;
}

export function readStoredAuthorization(key: string | undefined): string | undefined {
  if (!key || typeof sessionStorage === "undefined") {
    return undefined;
  }
  const raw = sessionStorage.getItem(key) ?? localStorage.getItem(key);
  if (raw == null || raw === "") {
    return undefined;
  }
  try {
    const parsed = JSON.parse(raw);
    return typeof parsed === "string" && parsed.trim() ? parsed : undefined;
  } catch {
    return raw.trim() ? raw : undefined;
  }
}

export function asOpaqueId(value: unknown): string | undefined {
  if (value == null || value === "") {
    return undefined;
  }
  return String(value);
}
