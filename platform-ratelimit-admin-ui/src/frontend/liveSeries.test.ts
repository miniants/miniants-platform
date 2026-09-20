import { describe, expect, it } from "vitest";
import { BucketsVo } from "./api";
import {
  MAX_LIVE_POINTS,
  appendLiveSnapshot,
  bucketSeriesKey,
  liveChartBounds,
  seriesLabel,
} from "./liveSeries";

describe("appendLiveSnapshot", () => {
  it("starts a series per bucket and appends remaining over time", () => {
    const first = appendLiveSnapshot([], {
      observedAt: "2026-08-29T06:00:00.000Z",
      buckets: [{
        policyCode: "auth.login",
        subject: "127.0.0.1",
        algorithm: "GCRA",
        limitCount: 20,
        remaining: 19,
        retryAfterMs: 0,
      }],
    });
    expect(first).toHaveLength(1);
    expect(first[0]?.key).toBe(bucketSeriesKey("auth.login", "127.0.0.1"));
    expect(seriesLabel(first[0]!)).toBe("auth.login · 127.0.0.1");

    const second = appendLiveSnapshot(first, {
      observedAt: "2026-08-29T06:00:05.000Z",
      buckets: [{
        policyCode: "auth.login",
        subject: "127.0.0.1",
        algorithm: "GCRA",
        limitCount: 20,
        remaining: 18,
        retryAfterMs: 0,
      }],
    });
    expect(second[0]?.points.map((point) => point.remaining)).toEqual([19, 18]);
    expect(second[0]?.latestRemaining).toBe(18);
  });

  it("can seed stored frames then append the current peek", () => {
    const frames: BucketsVo[] = [
      {
        observedAt: "2026-09-06T01:00:00.000Z",
        buckets: [{
          policyCode: "auth.login",
          subject: "127.0.0.1",
          algorithm: "GCRA",
          limitCount: 20,
          remaining: 19,
          retryAfterMs: 0,
        }],
      },
      {
        observedAt: "2026-09-06T01:00:30.000Z",
        buckets: [{
          policyCode: "auth.login",
          subject: "127.0.0.1",
          algorithm: "GCRA",
          limitCount: 20,
          remaining: 18,
          retryAfterMs: 0,
        }],
      },
    ];
    const seeded = frames.reduce(
      (series, frame) => appendLiveSnapshot(series, frame),
      [] as ReturnType<typeof appendLiveSnapshot>,
    );
    const live = appendLiveSnapshot(seeded, {
      observedAt: "2026-09-06T01:00:35.000Z",
      buckets: [{
        policyCode: "auth.login",
        subject: "127.0.0.1",
        algorithm: "GCRA",
        limitCount: 20,
        remaining: 17,
        retryAfterMs: 0,
      }],
    });
    expect(live[0]?.points.map((point) => point.remaining)).toEqual([19, 18, 17]);
  });

  it("caps history at max points", () => {
    let series = appendLiveSnapshot([], {
      observedAt: "2026-08-29T06:00:00.000Z",
      buckets: [{
        policyCode: "auth.login",
        subject: "127.0.0.1",
        algorithm: "GCRA",
        limitCount: 1,
        remaining: 1,
        retryAfterMs: 0,
      }],
    });
    for (let i = 1; i < MAX_LIVE_POINTS + 5; i += 1) {
      series = appendLiveSnapshot(series, {
        observedAt: new Date(Date.parse("2026-08-29T06:00:00.000Z") + i * 1000).toISOString(),
        buckets: [{
          policyCode: "auth.login",
          subject: "127.0.0.1",
          algorithm: "GCRA",
          limitCount: 1,
          remaining: 0,
          retryAfterMs: 1,
        }],
      });
    }
    expect(series[0]?.points).toHaveLength(MAX_LIVE_POINTS);
    expect(series[0]?.points[0]?.remaining).toBe(0);
  });
});

describe("liveChartBounds", () => {
  it("expands equal timestamps so the chart has a width", () => {
    const bounds = liveChartBounds([{
      key: "a",
      policyCode: "auth.login",
      subject: "1",
      algorithm: "GCRA",
      latestRemaining: 3,
      latestLimit: 10,
      points: [{ at: 1000, remaining: 3, limitCount: 10 }],
    }]);
    expect(bounds.minT).toBe(1000);
    expect(bounds.maxT).toBe(2000);
    expect(bounds.maxY).toBe(10);
  });
});
