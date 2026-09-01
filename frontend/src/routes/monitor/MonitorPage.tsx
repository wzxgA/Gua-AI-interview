import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ExternalLink } from 'lucide-react';
import { SilverButton } from '@/components/ui/silver-button';
import { PageHeader } from '@/components/common/PageHeader';
import { MonitorStatCard } from '@/components/monitor/MonitorStatCard';
import { MonitorChartCard, type ChartSeries } from '@/components/monitor/MonitorChartCard';
import { usePromInstant, usePromRange } from '@/hooks/usePrometheus';
import {
  fmtNumber,
  RATE_WINDOW,
  type PromSeries,
  type RangeKey,
} from '@/api/prometheus';

/** 剔除固定 tag（job/instance/application）后按指定标签拼接 series 展示名 */
function seriesName(metric: Record<string, string>, keys?: string[], override?: string): string {
  if (override) return override;
  const parts = (keys ?? []).map((k) => metric[k]).filter(Boolean);
  if (parts.length) return parts.join('/');
  const extra = Object.entries(metric).filter(
    ([k]) => !['job', 'instance', 'application'].includes(k),
  );
  return extra.length ? extra.map(([k, v]) => `${k}=${v}`).join('/') : 'value';
}

function toChartSeries(
  series: PromSeries[] | undefined,
  keys?: string[],
  override?: string,
): ChartSeries[] {
  return (series ?? []).map((s) => ({
    name: seriesName(s.metric, keys, override),
    points: s.values.map(([t, v]) => ({ x: t * 1000, y: Number(v) })),
  }));
}

/** 瞬时查询结果求和（无数据返回 null） */
function instantSum(instants: { value: [number, string] }[] | undefined): number | null {
  if (!instants || instants.length === 0) return null;
  return instants.reduce((acc, i) => acc + Number(i.value[1]), 0);
}

const RANGES: RangeKey[] = ['1h', '6h', '24h'];

/** 管理端监控页：自绘磨砂玻璃面板（recharts），数据经 /prometheus 代理拉取，30s 自动刷新 */
export function MonitorPage() {
  const { t } = useTranslation();
  const [range, setRange] = useState<RangeKey>('1h');
  const W = RATE_WINDOW[range];

  const grafanaUrl = import.meta.env.VITE_GRAFANA_URL || 'http://localhost:3000';

  // ─── 统计卡（instant） ───
  const roundCur = usePromInstant('aims_graph_round_current');
  const roundTot = usePromInstant('aims_graph_round_total');
  const cost = usePromInstant('sum(aims_llm_cost_total)');
  const tokens1h = usePromInstant('sum(increase(aims_llm_tokens_total[1h]))');
  const errors1h = usePromInstant('sum(increase(aims_graph_node_error_total[1h]))');
  const retries1h = usePromInstant('sum(increase(aims_graph_node_retry_total[1h]))');

  // ─── 图表卡（range） ───
  const durP50 = usePromRange(
    `histogram_quantile(0.50, sum by (le) (rate(aims_graph_node_duration_seconds_bucket[${W}])))`,
    range,
  );
  const durP95 = usePromRange(
    `histogram_quantile(0.95, sum by (le) (rate(aims_graph_node_duration_seconds_bucket[${W}])))`,
    range,
  );
  const durP99 = usePromRange(
    `histogram_quantile(0.99, sum by (le) (rate(aims_graph_node_duration_seconds_bucket[${W}])))`,
    range,
  );
  const nodeP95 = usePromRange(
    `histogram_quantile(0.95, sum by (le, node) (rate(aims_graph_node_duration_seconds_bucket[${W}])))`,
    range,
  );
  const llmP95 = usePromRange(
    `histogram_quantile(0.95, sum by (le, tier, model) (rate(aims_llm_latency_seconds_bucket[${W}])))`,
    range,
  );
  const tokenRate = usePromRange(
    `sum by (type) (rate(aims_llm_tokens_total[${W}]))`,
    range,
  );
  const errorRate = usePromRange(
    `sum by (node, error_type) (rate(aims_graph_node_error_total[${W}]))`,
    range,
  );
  const heapUsed = usePromRange('sum(jvm_memory_used_bytes{area="heap"})', range);
  const heapMax = usePromRange('sum(jvm_memory_max_bytes{area="heap"})', range);

  const nodeDurationSeries = useMemo(() => {
    const a = toChartSeries(durP50.data, undefined, 'P50');
    const b = toChartSeries(durP95.data, undefined, 'P95');
    const c = toChartSeries(durP99.data, undefined, 'P99');
    return [...a, ...b, ...c];
  }, [durP50.data, durP95.data, durP99.data]);

  const jvmSeries = useMemo(() => {
    const used = toChartSeries(heapUsed.data, undefined, t('monitor.legend.used'));
    const max = toChartSeries(heapMax.data, undefined, t('monitor.legend.max'));
    return [...used, ...max];
  }, [heapUsed.data, heapMax.data, t]);

  const roundValue = `${fmtNumber(instantSum(roundCur.data))} / ${fmtNumber(instantSum(roundTot.data))}`;
  const errorValue = `${fmtNumber(instantSum(errors1h.data))} / ${fmtNumber(instantSum(retries1h.data))}`;

  const openGrafana = () => {
    window.open(`${grafanaUrl}/d/aims-overview`, '_blank', 'noopener,noreferrer');
  };

  return (
    <div>
      <PageHeader
        title={t('sidebar.menu.monitor')}
        subtitle={t('monitor.subtitle')}
        action={
          <SilverButton variant="ghost" onClick={openGrafana} className="px-3 py-2 text-xs">
            <ExternalLink className="h-3.5 w-3.5" />
            {t('monitor.openInGrafana')}
          </SilverButton>
        }
      />

      <div className="mb-4 flex gap-2">
        {RANGES.map((r) => (
          <SilverButton
            key={r}
            variant={r === range ? 'primary' : 'ghost'}
            onClick={() => setRange(r)}
            className="px-3 py-1.5 text-xs"
          >
            {t(`monitor.range.${r}`)}
          </SilverButton>
        ))}
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MonitorStatCard label={t('monitor.stat.round')} value={roundValue} />
        <MonitorStatCard
          label={t('monitor.stat.cost')}
          value={fmtNumber(instantSum(cost.data))}
        />
        <MonitorStatCard
          label={t('monitor.stat.tokens1h')}
          value={fmtNumber(instantSum(tokens1h.data))}
        />
        <MonitorStatCard
          label={t('monitor.stat.errorRetry1h')}
          value={errorValue}
          hint={t('monitor.stat.errorRetryHint')}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <MonitorChartCard
          title={t('monitor.chart.nodeDuration')}
          series={nodeDurationSeries}
          unit="s"
          isLine
          isLoading={durP50.isLoading}
        />
        <MonitorChartCard
          title={t('monitor.chart.llmLatency')}
          series={toChartSeries(llmP95.data, ['tier', 'model'])}
          unit="s"
          isLine
          isLoading={llmP95.isLoading}
        />
        <MonitorChartCard
          title={t('monitor.chart.nodeP95')}
          series={toChartSeries(nodeP95.data, ['node'])}
          unit="s"
          isLine
          isLoading={nodeP95.isLoading}
        />
        <MonitorChartCard
          title={t('monitor.chart.tokenRate')}
          series={toChartSeries(tokenRate.data, ['type'])}
          unit="ops"
          noFill
          isLoading={tokenRate.isLoading}
        />
        <MonitorChartCard
          title={t('monitor.chart.errorRate')}
          series={toChartSeries(errorRate.data, ['node', 'error_type'])}
          unit="ops"
          isLine
          isLoading={errorRate.isLoading}
        />
        <MonitorChartCard
          title={t('monitor.chart.jvmHeap')}
          series={jvmSeries}
          unit="bytes"
          noFill
          isLoading={heapUsed.isLoading}
        />
      </div>
    </div>
  );
}
