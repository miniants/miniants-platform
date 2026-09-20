import { BucketVo, BucketsVo } from "./api";

export const MAX_LIVE_POINTS = 120;

export interface LiveSample {
  at: number;
  remaining: number;
  limitCount: number;
}

export interface LiveSeries {
  key: string;
  policyCode: string;
  subject: string;
  algorithm: string;
  points: LiveSample[];
  latestRemaining: number;
  latestLimit: number;
}

export function bucketSeriesKey(policyCode: string, subject: string): string {
  return `${policyCode}\0${subject}`;
}

export function seriesLabel(series: Pick<LiveSeries, "policyCode" | "subject">): string {
  return `${series.policyCode} · ${series.subject}`;
}

export function appendLiveSnapshot(
  current: LiveSeries[],
  snapshot: BucketsVo,
  now = Date.now(),
  maxPoints = MAX_LIVE_POINTS,
): LiveSeries[] {
  const parsed = snapshot.observedAt ? Date.parse(snapshot.observedAt) : now;
  const at = Number.isNaN(parsed) ? now : parsed;
  const byKey = new Map(current.map((item) => [item.key, item]));
  for (const bucket of snapshot.buckets ?? []) {
    const next = appendBucket(byKey.get(bucketSeriesKey(bucket.policyCode, bucket.subject)), bucket, at, maxPoints);
    byKey.set(next.key, next);
  }
  return [...byKey.values()];
}

function appendBucket(
  existing: LiveSeries | undefined,
  bucket: BucketVo,
  at: number,
  maxPoints: number,
): LiveSeries {
  const point: LiveSample = {
    at,
    remaining: bucket.remaining,
    limitCount: bucket.limitCount,
  };
  const points = existing ? [...existing.points, point].slice(-maxPoints) : [point];
  return {
    key: bucketSeriesKey(bucket.policyCode, bucket.subject),
    policyCode: bucket.policyCode,
    subject: bucket.subject,
    algorithm: bucket.algorithm,
    points,
    latestRemaining: bucket.remaining,
    latestLimit: bucket.limitCount,
  };
}

export function liveChartBounds(series: LiveSeries[]): { minT: number; maxT: number; maxY: number } {
  let minT = Number.POSITIVE_INFINITY;
  let maxT = Number.NEGATIVE_INFINITY;
  let maxY = 1;
  for (const item of series) {
    for (const point of item.points) {
      minT = Math.min(minT, point.at);
      maxT = Math.max(maxT, point.at);
      maxY = Math.max(maxY, point.remaining, point.limitCount);
    }
  }
  if (!Number.isFinite(minT)) {
    return { minT: 0, maxT: 1, maxY: 1 };
  }
  if (minT === maxT) {
    maxT = minT + 1000;
  }
  return { minT, maxT, maxY };
}

const PALETTE = [
  "#1677ff",
  "#52c41a",
  "#fa8c16",
  "#722ed1",
  "#eb2f96",
  "#13c2c2",
  "#f5222d",
  "#2f54eb",
];

export function seriesColor(index: number): string {
  return PALETTE[index % PALETTE.length] ?? PALETTE[0];
}
