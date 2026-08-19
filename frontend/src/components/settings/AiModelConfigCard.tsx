import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Loader2, Plus, RefreshCw, Save, Trash2, Zap } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { SilverButton } from '@/components/ui/silver-button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  useModelConfig,
  useSaveModelConfig,
  useTestModelConfig,
  useResetModelConfig,
  useDeleteModelConfigProvider,
} from '@/api/settings';

interface ProviderForm {
  name: string;
  baseUrl: string;
  apiKey: string;
  clearApiKey: boolean;
  source: 'db' | 'yml';
  /** true = yml 内置 provider（不可改名/删除）；false = DB 自定义 provider。 */
  builtin: boolean;
}

interface TierForm {
  tier: string;
  provider: string;
  model: string;
  overrideBaseUrl: string;
  overrideApiKey: string;
  clearOverrideApiKey: boolean;
}

const inputClass =
  'h-8 rounded-md border border-border-default bg-surface-overlay px-2 text-xs text-text-primary placeholder:text-text-muted focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30 transition-colors';

function maskLabel(masked: string | null): string {
  return masked && masked.length > 0 ? `*** ${masked} ***` : '';
}

export function AiModelConfigCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useModelConfig();
  const saveMutation = useSaveModelConfig();
  const testMutation = useTestModelConfig();
  const resetMutation = useResetModelConfig();
  const deleteMutation = useDeleteModelConfigProvider();

  const [providers, setProviders] = useState<ProviderForm[]>([]);
  const [tiers, setTiers] = useState<TierForm[]>([]);

  // 数据加载后初始化表单
  useEffect(() => {
    if (!data) return;
    setProviders(
      data.providers.map((p) => ({
        name: p.name,
        baseUrl: p.baseUrl,
        apiKey: '',
        clearApiKey: false,
        source: p.source,
        builtin: p.builtin,
      })),
    );
    setTiers(
      data.tiers.map((t) => ({
        tier: t.tier,
        // 后端可能以 "<TIER>@override" 内部 provider 生效，展示时还原为原始 provider
        provider: t.provider.replace(/@override$/, ''),
        model: t.model,
        overrideBaseUrl: t.overrideBaseUrl ?? '',
        overrideApiKey: '',
        clearOverrideApiKey: false,
      })),
    );
  }, [data]);

  const providerOptions = useMemo(() => {
    const saved = data?.providers ?? [];
    const savedNames = new Set(saved.map((p) => p.name));
    // 合并当前表单中尚未保存的新增服务商（已命名），使其立即可在档位独立配置中选择
    const draft = providers
      .filter((p) => p.name.trim().length > 0 && !savedNames.has(p.name.trim()))
      .map((p) => ({ value: p.name.trim(), label: p.name.trim() }));
    return [
      ...saved.map((p) => ({ value: p.name, label: p.name })),
      // 排序稳定：未命名草稿不展示；同名草稿去重（已保存优先）
      ...draft.filter((d, i) => draft.findIndex((x) => x.value === d.value) === i),
    ];
  }, [data, providers]);

  const hasLoaded = !!data && providers.length > 0;

  const buildCommand = () => ({
    providers: providers
      .filter((p) => p.name.trim().length > 0)
      .map((p) => ({
        name: p.name.trim(),
        baseUrl: p.baseUrl,
        // 未输入则保留 DB 旧值（undefined）；显式清除时传空串回退 yml
        apiKey: p.clearApiKey ? '' : p.apiKey || undefined,
      })),
    tiers: tiers.map((tm) => ({
      tier: tm.tier,
      provider: tm.provider || undefined,
      model: tm.model || undefined,
      // 空串表示清除 DB 覆盖（回退全局）；非空则覆盖
      overrideBaseUrl: tm.overrideBaseUrl,
      overrideApiKey: tm.clearOverrideApiKey ? '' : tm.overrideApiKey || undefined,
    })),
  });

  const handleSave = () => {
    saveMutation.mutate(buildCommand(), {
      onSuccess: () => {
        toast.success(t('settings.aiConfig.saveSuccess'));
        setProviders((prev) => prev.map((p) => ({ ...p, apiKey: '', clearApiKey: false })));
        setTiers((prev) =>
          prev.map((tm) => ({ ...tm, overrideApiKey: '', clearOverrideApiKey: false })),
        );
      },
      onError: (err: Error) => toast.error(err.message || t('settings.aiConfig.saveFailed')),
    });
  };

  const handleTest = () => {
    testMutation.mutate(buildCommand(), {
      onError: (err: Error) => toast.error(err.message || t('settings.aiConfig.testFailed')),
    });
  };

  const handleReset = () => {
    resetMutation.mutate(undefined, {
      onSuccess: () => toast.success(t('settings.aiConfig.resetSuccess')),
      onError: (err: Error) => toast.error(err.message || t('settings.aiConfig.resetFailed')),
    });
  };

  const handleAddProvider = () => {
    setProviders((prev) => [
      ...prev,
      {
        name: '',
        baseUrl: '',
        apiKey: '',
        clearApiKey: false,
        source: 'db',
        builtin: false,
      },
    ]);
  };

  const handleDeleteProvider = (name: string) => {
    deleteMutation.mutate(name, {
      onSuccess: () => toast.success(t('settings.aiConfig.deleteProviderSuccess', { name })),
      onError: (err: Error) => toast.error(err.message || t('settings.aiConfig.deleteProviderFailed')),
    });
  };

  return (
    <GlassCard className="p-6">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="text-sm font-medium text-text-primary">{t('settings.aiConfig.title')}</h3>
          <p className="mt-0.5 text-xs text-text-muted">{t('settings.aiConfig.subtitle')}</p>
        </div>
        {data?.defaultTier && (
          <span className="rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
            {t('settings.aiConfig.defaultTier')}: {data.defaultTier}
          </span>
        )}
      </div>

      {isLoading && !data ? (
        <div className="space-y-3">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : (
        <div className="space-y-6">
          {/* Provider 全局配置 */}
          <section>
            <div className="mb-2 flex items-center justify-between">
              <h4 className="text-xs font-medium uppercase tracking-wide text-text-muted">
                {t('settings.aiConfig.providers')}
              </h4>
              <SilverButton
                variant="ghost"
                onClick={handleAddProvider}
                className="px-2 py-1 text-[11px]"
              >
                <Plus className="h-3 w-3" />
                {t('settings.aiConfig.addProvider')}
              </SilverButton>
            </div>
            <div className="space-y-3">
              {providers.map((p, idx) => {
                const masked = data?.providers.find((x) => x.name === p.name)?.apiKeyMasked;
                return (
                  <div
                    key={p.name || `__new_${idx}`}
                    className="rounded-lg border border-border-subtle bg-surface-overlay/50 p-3"
                  >
                    <div className="flex items-center justify-between gap-2">
                      {p.builtin ? (
                        <span className="text-xs font-semibold text-silver-200">{p.name}</span>
                      ) : (
                        <Input
                          className={inputClass + ' w-44'}
                          value={p.name}
                          onChange={(e) =>
                            setProviders((prev) =>
                              prev.map((x, i) =>
                                i === idx ? { ...x, name: e.target.value } : x,
                              ),
                            )
                          }
                          placeholder={t('settings.aiConfig.providerNamePlaceholder')}
                        />
                      )}
                      <div className="flex items-center gap-1.5">
                        <span className="rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
                          {t('settings.aiConfig.source')}: {p.source}
                        </span>
                        {!p.builtin && (
                          <button
                            type="button"
                            onClick={() => {
                              if (p.name.trim()) {
                                handleDeleteProvider(p.name.trim());
                              } else {
                                setProviders((prev) => prev.filter((_, i) => i !== idx));
                              }
                            }}
                            className="rounded-md p-1 text-text-muted transition-colors hover:border-danger/40 hover:text-danger"
                            title={t('settings.aiConfig.deleteProvider')}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                    </div>
                    <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.baseUrl')}
                        </label>
                        <Input
                          className={inputClass}
                          value={p.baseUrl}
                          onChange={(e) =>
                            setProviders((prev) =>
                              prev.map((x, i) =>
                                i === idx ? { ...x, baseUrl: e.target.value } : x,
                              ),
                            )
                          }
                          placeholder={t('settings.aiConfig.baseUrlPlaceholder')}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.apiKey')}
                        </label>
                        <div className="flex gap-1.5">
                          <Input
                            type="password"
                            className={inputClass}
                            value={p.apiKey}
                            onChange={(e) =>
                              setProviders((prev) =>
                                prev.map((x, i) =>
                                  i === idx
                                    ? { ...x, apiKey: e.target.value, clearApiKey: false }
                                    : x,
                                ),
                              )
                            }
                            placeholder={
                              masked && masked.length > 0
                                ? maskLabel(masked)
                                : t('settings.aiConfig.apiKeyUnset')
                            }
                            autoComplete="new-password"
                          />
                          {masked && masked.length > 0 && (
                            <button
                              type="button"
                              onClick={() =>
                                setProviders((prev) =>
                                  prev.map((x, i) =>
                                    i === idx
                                      ? { ...x, apiKey: '', clearApiKey: true }
                                      : x,
                                  ),
                                )
                              }
                              className="shrink-0 rounded-md border border-border-default px-2 text-[11px] text-text-muted transition-colors hover:border-danger/40 hover:text-danger"
                              title={t('settings.aiConfig.clearKey')}
                            >
                              {t('settings.aiConfig.clearKey')}
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* 档位独立配置 */}
          <section>
            <h4 className="mb-2 text-xs font-medium uppercase tracking-wide text-text-muted">
              {t('settings.aiConfig.tiers')}
            </h4>
            <div className="space-y-3">
              {tiers.map((tm, idx) => {
                const masked = data?.tiers.find((x) => x.tier === tm.tier)?.overrideApiKeyMasked;
                return (
                  <div
                    key={tm.tier}
                    className="rounded-lg border border-border-subtle bg-surface-overlay/50 p-3"
                  >
                    <div className="mb-2 flex items-center justify-between">
                      <span className="text-xs font-semibold text-silver-200">{tm.tier}</span>
                      <span className="text-[10px] text-text-muted">
                        {t('settings.aiConfig.tierOverrideHint')}
                      </span>
                    </div>
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-4">
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.tierProvider')}
                        </label>
                        <Select
                          value={tm.provider}
                          onChange={(v) =>
                            setTiers((prev) =>
                              prev.map((x, i) => (i === idx ? { ...x, provider: v } : x)),
                            )
                          }
                          options={providerOptions}
                          title={t('settings.aiConfig.tierProvider')}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.tierModel')}
                        </label>
                        <Input
                          className={inputClass}
                          value={tm.model}
                          onChange={(e) =>
                            setTiers((prev) =>
                              prev.map((x, i) => (i === idx ? { ...x, model: e.target.value } : x)),
                            )
                          }
                          placeholder={t('settings.aiConfig.tierModelPlaceholder')}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.overrideBaseUrl')}
                        </label>
                        <Input
                          className={inputClass}
                          value={tm.overrideBaseUrl}
                          onChange={(e) =>
                            setTiers((prev) =>
                              prev.map((x, i) =>
                                i === idx ? { ...x, overrideBaseUrl: e.target.value } : x,
                              ),
                            )
                          }
                          placeholder={t('settings.aiConfig.overrideBaseUrlPlaceholder')}
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-[11px] text-text-muted">
                          {t('settings.aiConfig.overrideApiKey')}
                        </label>
                        <div className="flex gap-1.5">
                          <Input
                            type="password"
                            className={inputClass}
                            value={tm.overrideApiKey}
                            onChange={(e) =>
                              setTiers((prev) =>
                                prev.map((x, i) =>
                                  i === idx
                                    ? {
                                        ...x,
                                        overrideApiKey: e.target.value,
                                        clearOverrideApiKey: false,
                                      }
                                    : x,
                                ),
                              )
                            }
                            placeholder={
                              masked && masked.length > 0
                                ? maskLabel(masked)
                                : t('settings.aiConfig.apiKeyUnset')
                            }
                            autoComplete="new-password"
                          />
                          {masked && masked.length > 0 && (
                            <button
                              type="button"
                              onClick={() =>
                                setTiers((prev) =>
                                  prev.map((x, i) =>
                                    i === idx
                                      ? { ...x, overrideApiKey: '', clearOverrideApiKey: true }
                                      : x,
                                  ),
                                )
                              }
                              className="shrink-0 rounded-md border border-border-default px-2 text-[11px] text-text-muted transition-colors hover:border-danger/40 hover:text-danger"
                              title={t('settings.aiConfig.clearKey')}
                            >
                              {t('settings.aiConfig.clearKey')}
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>

          {/* 测试结果 */}
          {testMutation.data && (
            <section className="rounded-lg border border-border-subtle bg-surface-overlay/50 p-3">
              <h4 className="mb-2 text-xs font-medium text-text-primary">
                {t('settings.aiConfig.testResult')}
              </h4>
              <div className="space-y-1">
                {testMutation.data.results.map((r) => (
                  <div
                    key={r.tier}
                    className="flex items-center justify-between text-xs text-text-secondary"
                  >
                    <span>{r.tier}</span>
                    {r.success ? (
                      <span className="text-emerald-400">
                        {t('settings.aiConfig.testOk')} · {r.latencyMs}ms
                      </span>
                    ) : (
                      <span className="text-danger">{t('settings.aiConfig.testFail')}</span>
                    )}
                    {!r.success && r.error && (
                      <span className="max-w-[50%] truncate text-[11px] text-text-muted" title={r.error}>
                        {r.error}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* 操作按钮 */}
          <div className="flex flex-wrap items-center gap-2 border-t border-border-subtle pt-4">
            <SilverButton
              variant="ghost"
              onClick={handleTest}
              disabled={testMutation.isPending || !hasLoaded}
              className="px-4 py-2 text-xs"
            >
              {testMutation.isPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Zap className="h-3.5 w-3.5" />
              )}
              {t('settings.aiConfig.testConnection')}
            </SilverButton>
            <SilverButton
              onClick={handleSave}
              disabled={saveMutation.isPending || !hasLoaded}
              className="px-4 py-2 text-xs"
            >
              {saveMutation.isPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Save className="h-3.5 w-3.5" />
              )}
              {t('settings.aiConfig.saveAndApply')}
            </SilverButton>
            <SilverButton
              variant="ghost"
              onClick={handleReset}
              disabled={resetMutation.isPending || !hasLoaded}
              className="ml-auto px-4 py-2 text-xs"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              {t('settings.aiConfig.resetToDefault')}
            </SilverButton>
          </div>
        </div>
      )}
    </GlassCard>
  );
}
