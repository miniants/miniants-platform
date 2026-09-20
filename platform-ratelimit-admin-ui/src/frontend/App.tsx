import { FormEvent, useEffect, useRef, useState } from "react";
import {
  BucketsVo,
  EffectivePolicyVo,
  LIVE_REFRESH_SECONDS,
  LiveRefreshSeconds,
  PageResult,
  PolicySource,
  PolicyVo,
  RequestError,
  RevisionVo,
  RuntimeVo,
  UiConfig,
  asOpaqueId,
  isLiveRefreshSeconds,
  readStoredAuthorization,
  resolveApiBase,
  unwrap,
} from "./api";
import { LiveChart } from "./LiveChart";
import { LiveSeries, appendLiveSnapshot } from "./liveSeries";
import { PolicyDraft, summarizeImpact, validatePolicyDraft } from "./policyForm";

const emptyDraft = (): PolicyDraft => ({
  policyCode: "",
  algorithm: "GCRA",
  limitCount: 20,
  periodMs: 60_000,
  burst: 20,
  storeFailurePolicy: "DENY",
  enabled: true,
  remark: "",
});

let authorizationHeader = "";

function requestHeaders(init?: RequestInit): HeadersInit {
  const headers = new Headers(init?.headers);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (authorizationHeader && !headers.has("Authorization")) {
    headers.set("Authorization", authorizationHeader);
  }
  return headers;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: "same-origin",
    ...init,
    headers: requestHeaders(init),
  });
  const body = await response.json();
  return unwrap<T>(response.status, body);
}

function sourceLabel(source: PolicySource | undefined): string {
  switch (source) {
    case "dynamic":
      return "已发布";
    case "yaml":
      return "YAML 基线";
    case "builtin":
      return "内置";
    case undefined:
      return "—";
    default: {
      const exhausted: never = source;
      return exhausted;
    }
  }
}

function formatTime(value?: string): string {
  if (!value) {
    return "—";
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

type RuntimeTab = "effective" | "live";

export function App() {
  const [apiBase, setApiBase] = useState<string>("");
  const [records, setRecords] = useState<PolicyVo[]>([]);
  const [revisions, setRevisions] = useState<RevisionVo[]>([]);
  const [draft, setDraft] = useState<PolicyDraft>(emptyDraft());
  const [editingId, setEditingId] = useState<string | undefined>();
  const [version, setVersion] = useState<string | undefined>();
  const [runtime, setRuntime] = useState<RuntimeVo | null>(null);
  const [error, setError] = useState<string>("");
  const [confirmText, setConfirmText] = useState<string>("");
  const [pendingAction, setPendingAction] = useState<(() => Promise<void>) | null>(null);
  const [runtimeTab, setRuntimeTab] = useState<RuntimeTab>("effective");
  const [liveRefreshSeconds, setLiveRefreshSeconds] = useState<LiveRefreshSeconds>(5);
  const [liveBuckets, setLiveBuckets] = useState<BucketsVo | null>(null);
  const [liveSeries, setLiveSeries] = useState<LiveSeries[]>([]);
  const [liveError, setLiveError] = useState<string>("");
  const liveHistoryLoadedRef = useRef(false);

  const reload = async (base: string) => {
    const page = await request<PageResult>(`${base}/page?current=1&size=50`);
    setRecords((page.records ?? []).map((row) => ({
      ...row,
      id: asOpaqueId(row.id),
      version: asOpaqueId(row.version),
    })));
    const runtimeVo = await request<RuntimeVo>(`${base}/runtime`);
    setRuntime({ ...runtimeVo, revision: asOpaqueId(runtimeVo.revision) ?? "0" });
    const history = await request<RevisionVo[]>(`${base}/revisions?limit=10`);
    setRevisions(history.map((item) => ({ ...item, revision: asOpaqueId(item.revision) ?? "" })));
  };

  useEffect(() => {
    const boot = async () => {
      const response = await fetch("./config.json", { credentials: "same-origin" });
      const config = (await response.json()) as UiConfig;
      authorizationHeader = readStoredAuthorization(config.authorizationStorageKey) ?? "";
      const base = resolveApiBase(config, "../admin/rate-limit/policies");
      setApiBase(base);
      await reload(base);
    };
    boot().catch((ex: Error) => setError(ex.message));
  }, []);

  useEffect(() => {
    if (!apiBase || runtimeTab !== "live") {
      return undefined;
    }
    let cancelled = false;
    let timer = 0;
    const loadBuckets = async () => {
      try {
        const snapshot = await request<BucketsVo>(`${apiBase}/buckets?limit=500`);
        if (!cancelled) {
          setLiveBuckets(snapshot);
          setLiveSeries((prev) => appendLiveSnapshot(prev, snapshot));
          setLiveError("");
        }
      } catch (ex) {
        if (!cancelled) {
          setLiveError(ex instanceof Error ? ex.message : "读取实时数据失败");
        }
      }
    };
    const start = async () => {
      if (!liveHistoryLoadedRef.current) {
        try {
          const frames = await request<BucketsVo[]>(`${apiBase}/buckets/history`);
          if (!cancelled && frames != null && frames.length > 0) {
            setLiveSeries((prev) => frames.reduce(
              (series, frame) => appendLiveSnapshot(series, frame),
              prev,
            ));
          }
        } catch {
          // 预载失败时保持从空线开始
        }
        if (cancelled) {
          return;
        }
        liveHistoryLoadedRef.current = true;
      }
      await loadBuckets();
      if (cancelled) {
        return;
      }
      timer = window.setInterval(() => {
        void loadBuckets();
      }, liveRefreshSeconds * 1000);
    };
    void start();
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [apiBase, runtimeTab, liveRefreshSeconds]);

  const askConfirm = (text: string, action: () => Promise<void>) => {
    setError("");
    setConfirmText(text);
    setPendingAction(() => action);
  };

  const applyConflict = (ex: unknown) => {
    const err = ex as RequestError;
    if (err.latest) {
      const latest = err.latest;
      setDraft({
        policyCode: latest.policyCode,
        algorithm: latest.algorithm,
        limitCount: latest.limitCount,
        periodMs: latest.periodMs,
        burst: latest.burst,
        storeFailurePolicy: latest.storeFailurePolicy,
        enabled: latest.enabled,
        remark: latest.remark ?? "",
      });
      setEditingId(asOpaqueId(latest.id));
      setVersion(asOpaqueId(latest.version));
    }
    setError(err instanceof Error ? err.message : "操作失败");
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const errors = validatePolicyDraft(draft);
    if (errors.length > 0) {
      setError(errors.join("；"));
      return;
    }
    askConfirm(`将发布：${summarizeImpact(draft)}。确认保存？`, async () => {
      try {
        await request(`${apiBase}/save`, {
          method: "POST",
          body: JSON.stringify({
            id: editingId,
            version,
            ...draft,
          }),
        });
        setDraft(emptyDraft());
        setEditingId(undefined);
        setVersion(undefined);
      } catch (ex) {
        applyConflict(ex);
        throw ex;
      }
    });
  };

  const confirm = async () => {
    if (!pendingAction) {
      return;
    }
    try {
      await pendingAction();
      setConfirmText("");
      setPendingAction(null);
      await reload(apiBase);
    } catch (ex) {
      applyConflict(ex);
    }
  };

  const fillFromEffective = (row: EffectivePolicyVo) => {
    const existing = records.find((item) => item.policyCode === row.policyCode);
    setDraft({
      policyCode: row.policyCode,
      algorithm: row.algorithm,
      limitCount: existing?.limitCount ?? row.limitCount,
      periodMs: existing?.periodMs ?? row.periodMs,
      burst: existing?.burst ?? row.burst ?? row.limitCount,
      storeFailurePolicy: existing?.storeFailurePolicy ?? row.storeFailurePolicy,
      enabled: existing?.enabled ?? row.enabled,
      remark: existing?.remark ?? "",
    });
    setEditingId(existing?.id);
    setVersion(existing?.version);
    setError("");
  };

  return (
    <main className="page">
      <h1>限流策略</h1>
      <p className="hint">
        这里改的是命名策略并发布到各实例。登录口是否限流看注解/路径绑定。
        编码必须和后台 <code>@RateLimit(policy=…)</code> / 路径绑定完全一致。
        拒绝次数看 Grafana <code>platform.ratelimit.acquire</code>。
      </p>
      {error ? <p className="alert" role="alert">{error}</p> : null}
      {runtime ? (
        <section className="runtime">
          <article>
            <dl>
              <dt>后端</dt>
              <dd>{runtime.backend ?? "—"}</dd>
            </dl>
          </article>
          <article>
            <dl>
              <dt>已发布修订</dt>
              <dd>{runtime.revision}</dd>
            </dl>
          </article>
          <article className={runtime.snapshotStale ? "warn" : undefined}>
            <dl>
              <dt>快照</dt>
              <dd>{runtime.snapshotStale ? "已陈旧" : "正常"}</dd>
            </dl>
          </article>
          <article className={runtime.publishFailed ? "warn" : undefined}>
            <dl>
              <dt>发布</dt>
              <dd>{runtime.publishFailed ? "失败待对账" : "正常"}</dd>
            </dl>
          </article>
          <article>
            <dl>
              <dt>最近成功刷新</dt>
              <dd>{formatTime(runtime.lastSuccessfulRefreshAt)}</dd>
            </dl>
          </article>
        </section>
      ) : null}
      <section className="card effective">
        <div className="tabs" role="tablist">
          <button
            type="button"
            role="tab"
            className={runtimeTab === "effective" ? "active" : undefined}
            aria-selected={runtimeTab === "effective"}
            onClick={() => setRuntimeTab("effective")}
          >
            当前生效
          </button>
          <button
            type="button"
            role="tab"
            className={runtimeTab === "live" ? "active" : undefined}
            aria-selected={runtimeTab === "live"}
            onClick={() => setRuntimeTab("live")}
          >
            <span className={`live-dot ${runtimeTab === "live" ? "on" : ""}`} aria-hidden="true" />
            实时数据
          </button>
        </div>
        {runtimeTab === "effective" ? (
          <>
            <p className="hint">YAML / 已发布快照会立刻作用；下面「已保存」只含入库行。改额度请用同名编码保存并发布。</p>
            {runtime?.effectivePolicies?.length ? (
              <table>
                <thead>
                  <tr>
                    <th>编码</th>
                    <th>额度</th>
                    <th>来源</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {runtime.effectivePolicies.map((row) => (
                    <tr key={row.policyCode}>
                      <td><code>{row.policyCode}</code></td>
                      <td>{row.limitCount} / {row.periodMs}ms</td>
                      <td>{sourceLabel(row.source)}</td>
                      <td>
                        <button type="button" onClick={() => fillFromEffective(row)}>填入表单</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className="hint">本机还没有生效的命名策略。</p>
            )}
          </>
        ) : (
          <>
            <div className="live-toolbar">
              <span className="live-status">
                <span className="live-dot on" aria-hidden="true" />
                剩余额度时序
              </span>
              <label>
                自动刷新
                <select
                  value={liveRefreshSeconds}
                  onChange={(e) => {
                    const next = Number(e.target.value);
                    if (isLiveRefreshSeconds(next)) {
                      setLiveRefreshSeconds(next);
                    }
                  }}
                >
                  {LIVE_REFRESH_SECONDS.map((seconds) => (
                    <option key={seconds} value={seconds}>{seconds}s</option>
                  ))}
                </select>
              </label>
              <span className="hint" style={{ margin: 0 }}>
                {formatTime(liveBuckets?.observedAt)}
                {liveBuckets?.truncated ? "　已截断前 500 条" : ""}
              </span>
            </div>
            {liveError ? <p className="alert" role="alert">{liveError}</p> : null}
            <LiveChart series={liveSeries} />
          </>
        )}
      </section>
      <div className="toolbar">
        <button
          type="button"
          className="primary"
          onClick={() => askConfirm("确认重新发布当前全部策略？", () => request(`${apiBase}/republish`, { method: "POST" }))}
        >
          重新发布
        </button>
        <button type="button" onClick={() => apiBase && reload(apiBase)}>刷新</button>
      </div>
      <div className="layout">
        <section className="card">
          <h2>已保存策略</h2>
          <table>
            <thead>
              <tr>
                <th>编码</th>
                <th>算法</th>
                <th>额度</th>
                <th>状态</th>
                <th>备注</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {records.length === 0 ? (
                <tr>
                  <td colSpan={6}>
                    还没有入库策略。
                    {runtime?.availablePolicies?.length
                      ? ` 后台已挂：${runtime.availablePolicies.join("、")}。`
                      : " YAML 基线已在跑。"}
                    要改额度请用同名编码「保存并发布」。
                  </td>
                </tr>
              ) : records.map((row) => (
                <tr key={row.id ?? row.policyCode}>
                  <td>{row.policyCode}</td>
                  <td>{row.algorithm}</td>
                  <td>{row.limitCount} / {row.periodMs}ms</td>
                  <td>{row.enabled ? "启用" : "停用"}</td>
                  <td>{row.remark ?? ""}</td>
                  <td>
                    <div className="actions">
                      <button
                        type="button"
                        onClick={() => {
                          setDraft({
                            policyCode: row.policyCode,
                            algorithm: row.algorithm,
                            limitCount: row.limitCount,
                            periodMs: row.periodMs,
                            burst: row.burst,
                            storeFailurePolicy: row.storeFailurePolicy,
                            enabled: row.enabled,
                            remark: row.remark ?? "",
                          });
                          setEditingId(row.id);
                          setVersion(row.version);
                        }}
                      >
                        编辑
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          askConfirm(
                            `确认${row.enabled ? "停用" : "启用"} ${row.policyCode}？`,
                            () =>
                              request(`${apiBase}/${row.id}/${row.enabled ? "disable" : "enable"}`, {
                                method: "POST",
                              }),
                          )
                        }
                      >
                        {row.enabled ? "停用" : "启用"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        <section className="card">
          <form onSubmit={submit}>
            <h2>{editingId ? "编辑策略" : "新建策略"}</h2>
            <label>
              编码
              <input
                value={draft.policyCode}
                onChange={(e) => setDraft({ ...draft, policyCode: e.target.value })}
              />
            </label>
            <label>
              算法
              <select
                value={draft.algorithm}
                onChange={(e) => setDraft({ ...draft, algorithm: e.target.value as PolicyDraft["algorithm"] })}
              >
                <option value="GCRA">GCRA</option>
                <option value="SLIDING_WINDOW">滑动窗口</option>
              </select>
            </label>
            <label>
              额度
              <input
                type="number"
                value={draft.limitCount}
                onChange={(e) => setDraft({ ...draft, limitCount: Number(e.target.value) })}
              />
            </label>
            <label>
              周期毫秒
              <input
                type="number"
                value={draft.periodMs}
                onChange={(e) => setDraft({ ...draft, periodMs: Number(e.target.value) })}
              />
            </label>
            {draft.algorithm === "GCRA" ? (
              <label>
                突发
                <input
                  type="number"
                  value={draft.burst ?? draft.limitCount}
                  onChange={(e) => setDraft({ ...draft, burst: Number(e.target.value) })}
                />
              </label>
            ) : null}
            <label>
              故障语义
              <select
                value={draft.storeFailurePolicy}
                onChange={(e) =>
                  setDraft({ ...draft, storeFailurePolicy: e.target.value as PolicyDraft["storeFailurePolicy"] })
                }
              >
                <option value="DENY">DENY</option>
                <option value="ALLOW">ALLOW</option>
              </select>
            </label>
            <label>
              备注
              <input
                value={draft.remark ?? ""}
                onChange={(e) => setDraft({ ...draft, remark: e.target.value })}
              />
            </label>
            <label className="check">
              启用
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(e) => setDraft({ ...draft, enabled: e.target.checked })}
              />
            </label>
            <button type="submit">保存并发布</button>
          </form>
        </section>
      </div>
      <section className="card" style={{ marginTop: 16 }}>
        <h2>版本历史</h2>
        <ul className="history">
          {revisions.map((item) => (
            <li key={item.revision}>
              <span>修订 {item.revision}　{item.createdAt ?? ""}　{item.createdBy ?? ""}</span>
              <button
                type="button"
                onClick={() =>
                  askConfirm(`确认回滚到修订 ${item.revision}？当前运行策略将被覆盖。`, () =>
                    request(`${apiBase}/revisions/${item.revision}/rollback`, { method: "POST" }),
                  )
                }
              >
                回滚
              </button>
            </li>
          ))}
        </ul>
      </section>
      {confirmText ? (
        <div className="dialog" role="dialog">
          <div>
            <p>{confirmText}</p>
            <div className="toolbar">
              <button type="button" className="primary" onClick={confirm}>确认</button>
              <button
                type="button"
                onClick={() => {
                  setConfirmText("");
                  setPendingAction(null);
                }}
              >
                取消
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </main>
  );
}
