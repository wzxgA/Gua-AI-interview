import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Check, Settings2 } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { PageHeader } from '@/components/common/PageHeader';
import { SilverButton } from '@/components/ui/silver-button';
import { Skeleton } from '@/components/ui/skeleton';
import { useModelTiers } from '@/api/settings';
import { AiModelConfigCard } from '@/components/settings/AiModelConfigCard';
import { TtsConfigCard } from '@/components/settings/TtsConfigCard';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { useLanguage } from '@/contexts/LanguageContext';
import { useEnumLabel } from '@/hooks/useEnumLabel';
import { cn } from '@/lib/utils';

export function SettingsPage() {
  const { t } = useTranslation();
  const { data, isLoading } = useModelTiers();
  const { language, setLanguage } = useLanguage();
  const enumLabel = useEnumLabel();
  // 默认只展示档位概览，AI 模型配置点击“配置”后展开
  const [configOpen, setConfigOpen] = useState(false);
  // TTS 语音配置折叠展开
  const [ttsOpen, setTtsOpen] = useState(false);

  return (
    <div>
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />

      <div className="space-y-6">
        <GlassCard className="p-6">
          <h3 className="mb-1 text-sm font-medium text-text-primary">{t('language.title')}</h3>
          <p className="mb-4 text-xs text-text-muted">{t('language.subtitle')}</p>
          <div className="space-y-2">
            {SUPPORTED_LANGUAGES.map((lang) => {
              const selected = lang.code === language;
              return (
                <button
                  key={lang.code}
                  onClick={() => setLanguage(lang.code)}
                  className={cn(
                    'flex w-full items-center justify-between rounded-md border px-4 py-2.5 text-sm transition-all',
                    selected
                      ? 'border-border-strong bg-surface-hover text-silver-100'
                      : 'border-border-subtle text-text-secondary hover:bg-surface-overlay hover:text-text-primary',
                  )}
                >
                  <span>{lang.label}</span>
                  {selected && <Check className="h-4 w-4" />}
                </button>
              );
            })}
          </div>
        </GlassCard>

        <GlassCard className="p-6">
          <div className="mb-4 flex items-center justify-between gap-2">
            <h3 className="text-sm font-medium text-text-primary">{t('settings.modelTiers')}</h3>
            <SilverButton
              variant="ghost"
              onClick={() => setConfigOpen((o) => !o)}
              className="px-2 py-1 text-[11px]"
            >
              <Settings2 className="h-3 w-3" />
              {t(configOpen ? 'settings.aiConfig.collapse' : 'settings.aiConfig.configure')}
            </SilverButton>
          </div>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : (
            <div className="space-y-3">
              {data?.tiers.map((t_) => (
                <div
                  key={t_.tier}
                  className="flex items-center justify-between border-b border-border-subtle pb-3 last:border-0 last:pb-0"
                >
                  <div>
                    <span className="text-sm font-medium text-silver-200">{t_.tier}</span>
                    {t_.tier === data.defaultTier && (
                      <span className="ml-2 rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
                        {t('settings.defaultBadge')}
                      </span>
                    )}
                    <p className="mt-0.5 text-xs text-text-muted">
                      {enumLabel('tier', t_.tier, t_.tier)}
                    </p>
                  </div>
                  <div className="text-right text-xs text-text-secondary">
                    <p>
                      {t_.provider} / {t_.model}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </GlassCard>

        {configOpen && <AiModelConfigCard />}

        <GlassCard className="p-6">
          <div className="mb-4 flex items-center justify-between gap-2">
            <h3 className="text-sm font-medium text-text-primary">{t('settings.ttsConfig.title')}</h3>
            <SilverButton
              variant="ghost"
              onClick={() => setTtsOpen((o) => !o)}
              className="px-2 py-1 text-[11px]"
            >
              <Settings2 className="h-3 w-3" />
              {t(ttsOpen ? 'settings.ttsConfig.collapse' : 'settings.ttsConfig.configure')}
            </SilverButton>
          </div>
          {ttsOpen && <TtsConfigCard />}
        </GlassCard>

      </div>
    </div>
  );
}
