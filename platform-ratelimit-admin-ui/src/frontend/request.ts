export interface ApiEnvelope<T> {
  code?: number | string;
  message?: string;
  data?: T;
}

export interface ConflictError<T> extends Error {
  conflict: true;
  latest?: T;
}

export function apiBaseFromConfig(config: { apiBase?: string; contextPath?: string }): string {
  if (config.apiBase && config.apiBase.trim()) {
    return config.apiBase.replace(/\/$/, "");
  }
  const context = config.contextPath ?? "";
  return `${context}/platform/admin/rate-limit/policies`;
}

export function unwrapEnvelope<T>(body: ApiEnvelope<T> | T, httpStatus: number): T {
  if (body && typeof body === "object" && "code" in body) {
    const envelope = body as ApiEnvelope<T>;
    if (Number(envelope.code) !== 200) {
      const error = new Error(envelope.message ?? "请求失败") as ConflictError<T>;
      if (httpStatus === 409) {
        error.conflict = true;
        error.latest = envelope.data;
      }
      throw error;
    }
    return (envelope.data ?? body) as T;
  }
  return body as T;
}

export async function parseApiResponse<T>(response: Response): Promise<T> {
  if (response.status === 401 || response.status === 403) {
    throw new Error("没有权限或登录已过期");
  }
  const body = (await response.json()) as ApiEnvelope<T>;
  if (response.status === 409) {
    const error = new Error(body.message ?? "版本冲突，请刷新后重试") as ConflictError<T>;
    error.conflict = true;
    error.latest = body.data;
    throw error;
  }
  return unwrapEnvelope(body, response.status);
}
