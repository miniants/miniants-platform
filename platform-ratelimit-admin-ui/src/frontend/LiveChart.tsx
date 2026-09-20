import { PointerEvent, useMemo, useState } from "react";
import { LiveSeries, liveChartBounds, seriesColor, seriesLabel } from "./liveSeries";

const WIDTH = 720;
const HEIGHT = 280;
const PAD_L = 42;
const PAD_R = 12;
const PAD_T = 12;
const PAD_B = 28;

function formatClock(at: number): string {
  return new Date(at).toLocaleTimeString();
}

function algorithmLabel(algorithm: string): string {
  if (algorithm === "SLIDING_WINDOW") {
    return "滑动窗口";
  }
  return algorithm || "—";
}

export function LiveChart({ series }: { series: LiveSeries[] }) {
  const [hoverX, setHoverX] = useState<number | null>(null);
  const bounds = useMemo(() => liveChartBounds(series), [series]);
  const plotW = WIDTH - PAD_L - PAD_R;
  const plotH = HEIGHT - PAD_T - PAD_B;
  const spanT = Math.max(1, bounds.maxT - bounds.minT);

  const xOf = (at: number) => PAD_L + ((at - bounds.minT) / spanT) * plotW;
  const yOf = (value: number) => PAD_T + (1 - value / bounds.maxY) * plotH;

  const hoverAt = hoverX == null
    ? null
    : bounds.minT + ((hoverX - PAD_L) / plotW) * spanT;

  const nearest = useMemo(() => {
    if (hoverAt == null || series.length === 0) {
      return null;
    }
    let best = series[0]?.points[0]?.at ?? bounds.minT;
    let bestDist = Number.POSITIVE_INFINITY;
    for (const item of series) {
      for (const point of item.points) {
        const dist = Math.abs(point.at - hoverAt);
        if (dist < bestDist) {
          bestDist = dist;
          best = point.at;
        }
      }
    }
    return best;
  }, [hoverAt, series, bounds.minT]);

  const onMove = (event: PointerEvent<SVGSVGElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * WIDTH;
    if (x < PAD_L || x > WIDTH - PAD_R) {
      setHoverX(null);
      return;
    }
    setHoverX(x);
  };

  const yTicks = [0, Math.round(bounds.maxY / 2), bounds.maxY];
  const xTicks = [bounds.minT, bounds.minT + spanT / 2, bounds.maxT];

  if (series.length === 0) {
    return <p className="hint">还没有活跃桶。打一次被限流的口后会出现明文 IP / 主体折线。</p>;
  }

  return (
    <div className="live-chart">
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        role="img"
        aria-label="限流桶剩余额度时序"
        onPointerMove={onMove}
        onPointerLeave={() => setHoverX(null)}
      >
        {yTicks.map((tick) => (
          <g key={`y-${tick}`}>
            <line
              x1={PAD_L}
              x2={WIDTH - PAD_R}
              y1={yOf(tick)}
              y2={yOf(tick)}
              className="grid"
            />
            <text x={PAD_L - 8} y={yOf(tick) + 4} textAnchor="end" className="axis">
              {tick}
            </text>
          </g>
        ))}
        {xTicks.map((tick) => (
          <text key={`x-${tick}`} x={xOf(tick)} y={HEIGHT - 8} textAnchor="middle" className="axis">
            {formatClock(tick)}
          </text>
        ))}
        {series.map((item, index) => {
          const d = item.points
            .map((point, pointIndex) => `${pointIndex === 0 ? "M" : "L"} ${xOf(point.at)} ${yOf(point.remaining)}`)
            .join(" ");
          return (
            <path
              key={item.key}
              d={d}
              fill="none"
              stroke={seriesColor(index)}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          );
        })}
        {nearest != null ? (
          <line
            x1={xOf(nearest)}
            x2={xOf(nearest)}
            y1={PAD_T}
            y2={HEIGHT - PAD_B}
            className="cursor"
          />
        ) : null}
      </svg>
      <ul className="live-legend">
        {series.map((item, index) => {
          const atHover = nearest == null
            ? undefined
            : item.points.reduce((best, point) =>
              Math.abs(point.at - nearest) < Math.abs(best.at - nearest) ? point : best);
          return (
            <li key={item.key}>
              <span className="swatch" style={{ background: seriesColor(index) }} />
              <code>{seriesLabel(item)}</code>
              <span>{algorithmLabel(item.algorithm)}</span>
              <strong>
                {(atHover?.remaining ?? item.latestRemaining)} / {item.latestLimit}
              </strong>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
