import { GlassCard } from '@/components/ui/glass-card';
import { PageHeader } from '@/components/common/PageHeader';
import { Skeleton } from '@/components/ui/skeleton';
import { useModelTiers } from '@/api/settings';
import { APP_VERSION, TIER_LABELS } from '@/lib/constants';

export function SettingsPage() {
  const { data, isLoading } = useModelTiers();

  return (
    <div>
      <PageHeader title="设置" subtitle="模型档位与平台配置" />

      <div className="space-y-6">
        <GlassCard className="p-6">
          <h3 className="mb-4 text-sm font-medium text-text-primary">AI 模型档位</h3>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : (
            <div className="space-y-3">
              {data?.tiers.map((t) => (
                <div
                  key={t.tier}
                  className="flex items-center justify-between border-b border-border-subtle pb-3 last:border-0 last:pb-0"
                >
                  <div>
                    <span className="text-sm font-medium text-silver-200">{t.tier}</span>
                    {t.tier === data.defaultTier && (
                      <span className="ml-2 rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
                        默认
                      </span>
                    )}
                    <p className="mt-0.5 text-xs text-text-muted">
                      {TIER_LABELS[t.tier] ?? t.tier}
                    </p>
                  </div>
                  <div className="text-right text-xs text-text-secondary">
                    <p>
                      {t.provider} / {t.model}
                    </p>
                    <p className="mt-0.5 text-text-muted">
                      {t.dimensions != null
                        ? `${t.dimensions} 维`
                        : [
                            t.temperature != null && `温度 ${t.temperature}`,
                            t.maxTokens != null && `maxTokens ${t.maxTokens}`,
                          ]
                            .filter(Boolean)
                            .join(' · ')}
                      {t.fallback && ` · 降级 ${t.fallback}`}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </GlassCard>

        <GlassCard className="p-6">
          <h3 className="mb-4 text-sm font-medium text-text-primary">环境信息</h3>
          <div className="space-y-2 text-xs text-text-secondary">
            <div className="flex justify-between">
              <span>前端版本</span>
              <span>{APP_VERSION}</span>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
