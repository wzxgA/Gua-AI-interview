import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { ChevronDown, Loader2, RefreshCw, Save, Settings2 } from 'lucide-react';
import { GlassCard } from '@/components/ui/glass-card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { SilverButton } from '@/components/ui/silver-button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  useTtsConfig,
  useSaveTtsConfig,
  useResetTtsConfig,
  type SaveTtsConfigCommand,
} from '@/api/settings';
import { cn } from '@/lib/utils';

/** 火山豆包常用音色建议。 */
const SPEAKER_PRESETS = [
  'zh_male_m191_uranus_bigtts',
  'zh_female_vv_uranus_bigtts',
  'zh_male_taocheng_uranus_bigtts',
  'zh_female_yueya_uranus_bigtts',
  'zh_male_eboy_uranus_bigtts',
];

const CUSTOM = '__custom__';
const FORMATS = ['mp3', 'wav', 'pcm'];
const SAMPLE_RATES = [16000, 24000, 48000];

/** 需展示来源徽标的内置字段。 */
const SOURCE_FIELDS = [
  'baseUrl',
  'apiKey',
  'resourceId',
  'defaultSpeaker',
] as const;

const inputClass =
  'h-8 rounded-md border border-border-default bg-surface-overlay px-2 text-xs text-text-primary placeholder:text-text-muted focus:border-silver-300/50 focus:outline-none focus:ring-1 focus:ring-silver-300/30 transition-colors';

interface FormState {
  enabled: boolean;
  provider: string;
  baseUrl: string;
  apiKey: string;
  resourceId: string;
  format: string;
  sampleRate: string;
  speechRate: string;
  personaVoiceLink: boolean;
  /** 预置音色选择（custom 模式时为空）。 */
  speaker: string;
}

export function TtsConfigCard() {
  const { t } = useTranslation();
  const { data, isLoading } = useTtsConfig();
  const saveMutation = useSaveTtsConfig();
  const resetMutation = useResetTtsConfig();

  const [form, setForm] = useState<FormState>({
    enabled: false,
    provider: 'volc',
    baseUrl: '',
    apiKey: '',
    resourceId: '',
    format: 'mp3',
    sampleRate: '24000',
    speechRate: '1',
    personaVoiceLink: false,
    speaker: '',
  });
  /** 用户实际改动过的字段，仅这些会随保存提交；未 touch 的字段保持「沿用 yml / 保留 DB」。 */
  const [touched, setTouched] = useState<Set<string>>(new Set());
  /** 音色：是否为自定义输入模式。 */
  const [speakerMode, setSpeakerMode] = useState<'preset' | 'custom'>('preset');
  const [customSpeaker, setCustomSpeaker] = useState('');
  const [advancedOpen, setAdvancedOpen] = useState(false);

  // 数据加载后初始化表单
  useEffect(() => {
    if (!data) return;
    const speaker = data.defaultSpeaker ?? '';
    const isPreset = SPEAKER_PRESETS.includes(speaker);
    setForm({
      enabled: !!data.enabled,
      provider: data.provider ?? '',
      baseUrl: data.baseUrl ?? '',
      apiKey: '',
      resourceId: data.resourceId ?? '',
      format: data.format ?? 'mp3',
      sampleRate: data.sampleRate != null ? String(data.sampleRate) : '',
      speechRate: data.speechRate != null ? String(data.speechRate) : '',
      personaVoiceLink: !!data.personaVoiceLink,
      speaker: isPreset ? speaker : '',
    });
    setSpeakerMode(isPreset ? 'preset' : 'custom');
    setCustomSpeaker(isPreset ? '' : speaker);
    setTouched(new Set()); // 数据重载后视为未修改
  }, [data]);

  const markTouched = (key: string) => {
    setTouched((prev) => {
      const next = new Set(prev);
      next.add(key);
      return next;
    });
  };

  const speakerValue = speakerMode === 'custom' ? customSpeaker : form.speaker;
  const hasLoaded = !!data;

  // 音色 Select 的当前选中项
  const selectedSpeakerOption =
    speakerMode === 'custom'
      ? CUSTOM
      : SPEAKER_PRESETS.includes(form.speaker)
        ? form.speaker
        : CUSTOM;

  const buildCommand = (): SaveTtsConfigCommand => {
    const cmd: SaveTtsConfigCommand = {};
    if (touched.has('enabled')) cmd.enabled = form.enabled;
    if (touched.has('provider')) cmd.provider = form.provider || undefined;
    if (touched.has('baseUrl')) cmd.baseUrl = form.baseUrl; // 空串=回退 yml
    if (touched.has('apiKey')) cmd.apiKey = form.apiKey; // 空串=清除回退 yml
    if (touched.has('resourceId')) cmd.resourceId = form.resourceId;
    if (touched.has('format')) cmd.format = form.format;
    if (touched.has('sampleRate')) cmd.sampleRate = form.sampleRate ? Number(form.sampleRate) : undefined;
    if (touched.has('speechRate')) cmd.speechRate = form.speechRate ? Number(form.speechRate) : undefined;
    if (touched.has('personaVoiceLink')) cmd.personaVoiceLink = form.personaVoiceLink;
    if (touched.has('speaker')) cmd.defaultSpeaker = speakerValue.trim() || undefined;
    return cmd;
  };

  const handleSave = () => {
    saveMutation.mutate(buildCommand(), {
      onSuccess: () => {
        toast.success(t('settings.ttsConfig.saveSuccess'));
        setForm((prev) => ({ ...prev, apiKey: '' }));
        setTouched((prev) => {
          const next = new Set(prev);
          next.delete('apiKey');
          return next;
        });
      },
      onError: (err: Error) => toast.error(err.message || t('settings.ttsConfig.saveFailed')),
    });
  };

  const handleReset = () => {
    resetMutation.mutate(undefined, {
      onSuccess: () => toast.success(t('settings.ttsConfig.resetSuccess')),
      onError: (err: Error) => toast.error(err.message || t('settings.ttsConfig.resetFailed')),
    });
  };

  const sourceOf = (field: (typeof SOURCE_FIELDS)[number]): 'db' | 'yml' => {
    const key =
      field === 'apiKey'
        ? 'apiKeySource'
        : field === 'baseUrl'
          ? 'baseUrlSource'
          : field === 'resourceId'
            ? 'resourceIdSource'
            : 'defaultSpeakerSource';
    return (data as unknown as Record<string, 'db' | 'yml'>)[key];
  };

  const sourceBadge = (field: (typeof SOURCE_FIELDS)[number]) => (
    <span className="ml-2 rounded bg-surface-hover px-1.5 py-0.5 text-[10px] text-text-muted">
      {t('settings.ttsConfig.source')}: {sourceOf(field)}
    </span>
  );

  return (
    <GlassCard className="p-6">
      <div className="mb-4 flex items-center justify-between">
        <div>
          <h3 className="text-sm font-medium text-text-primary">{t('settings.ttsConfig.title')}</h3>
          <p className="mt-0.5 text-xs text-text-muted">{t('settings.ttsConfig.subtitle')}</p>
        </div>
      </div>

      {isLoading && !data ? (
        <div className="space-y-3">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : (
        <div className="space-y-6">
          {/* 基础配置 */}
          <section className="space-y-3">
            {/* 全局启用 */}
            <button
              type="button"
              onClick={() => {
                markTouched('enabled');
                setForm((prev) => ({ ...prev, enabled: !prev.enabled }));
              }}
              className="flex w-full items-start gap-3 rounded-lg border border-border-default bg-surface-overlay px-3 py-3 text-left transition-colors hover:border-border-strong"
            >
              <Checkbox checked={form.enabled} readOnly className="mt-0.5" />
              <span>
                <span className="block text-sm font-medium text-text-primary">
                  {t('settings.ttsConfig.enabled')}
                </span>
                <span className="mt-0.5 block text-xs text-text-muted">
                  {t('settings.ttsConfig.enabledHint')}
                </span>
              </span>
            </button>

            <div>
              <label className="mb-1 block text-[11px] text-text-muted">
                {t('settings.ttsConfig.baseUrl')}
                {sourceBadge('baseUrl')}
              </label>
              <Input
                className={inputClass}
                value={form.baseUrl}
                onChange={(e) => {
                  markTouched('baseUrl');
                  setForm((prev) => ({ ...prev, baseUrl: e.target.value }));
                }}
                placeholder={t('settings.ttsConfig.baseUrlPlaceholder')}
              />
            </div>

            <div>
              <label className="mb-1 block text-[11px] text-text-muted">
                {t('settings.ttsConfig.apiKey')}
                {sourceBadge('apiKey')}
              </label>
              <div className="flex gap-1.5">
                <Input
                  type="password"
                  className={inputClass}
                  value={form.apiKey}
                  onChange={(e) => {
                    markTouched('apiKey');
                    setForm((prev) => ({ ...prev, apiKey: e.target.value }));
                  }}
                  placeholder={
                    (data?.apiKeyMasked && data.apiKeyMasked.length > 0)
                      ? `*** ${data.apiKeyMasked} ***`
                      : t('settings.ttsConfig.apiKeyUnset')
                  }
                  autoComplete="new-password"
                />
                {data?.apiKeyMasked && data.apiKeyMasked.length > 0 && !form.apiKey && (
                  <button
                    type="button"
                    onClick={() => {
                      markTouched('apiKey');
                      setForm((prev) => ({ ...prev, apiKey: '' }));
                    }}
                    className="shrink-0 rounded-md border border-border-default px-2 text-[11px] text-text-muted transition-colors hover:border-danger/40 hover:text-danger"
                    title={t('settings.ttsConfig.clearKey')}
                  >
                    {t('settings.ttsConfig.clearKey')}
                  </button>
                )}
              </div>
            </div>

            {/* 默认音色 */}
            <div>
              <label className="mb-1 block text-[11px] text-text-muted">
                {t('settings.ttsConfig.defaultSpeaker')}
                {sourceBadge('defaultSpeaker')}
              </label>
              {speakerMode === 'preset' ? (
                <Select
                  value={selectedSpeakerOption}
                  onChange={(v) => {
                    markTouched('speaker');
                    if (v === CUSTOM) {
                      setSpeakerMode('custom');
                    } else {
                      setForm((prev) => ({ ...prev, speaker: v }));
                    }
                  }}
                  options={[
                    ...SPEAKER_PRESETS.map((s) => ({ value: s, label: s })),
                    { value: CUSTOM, label: `✎ ${t('settings.ttsConfig.speakerCustom')}` },
                  ]}
                  title={t('settings.ttsConfig.speakerPresets')}
                />
              ) : (
                <div className="flex gap-1.5">
                  <Input
                    className={inputClass}
                    value={customSpeaker}
                    onChange={(e) => {
                      markTouched('speaker');
                      setCustomSpeaker(e.target.value);
                    }}
                    placeholder={t('settings.ttsConfig.speakerCustomLabel')}
                  />
                  <button
                    type="button"
                    onClick={() => setSpeakerMode('preset')}
                    className="shrink-0 rounded-md border border-border-default px-2 text-[11px] text-text-muted transition-colors hover:border-silver-300/50"
                  >
                    {t('settings.ttsConfig.speakerPresets')}
                  </button>
                </div>
              )}
            </div>
          </section>

          {/* 高级设置 */}
          <section>
            <button
              type="button"
              onClick={() => setAdvancedOpen((o) => !o)}
              className="mb-2 flex w-full items-center justify-between rounded-md border border-border-subtle px-3 py-2 text-xs text-text-muted transition-colors hover:text-text-primary"
            >
              <span className="flex items-center gap-1.5">
                <Settings2 className="h-3.5 w-3.5" />
                {t('settings.ttsConfig.advanced')}
              </span>
              <ChevronDown
                className={cn('h-4 w-4 transition-transform', advancedOpen && 'rotate-180')}
              />
            </button>
            {advancedOpen && (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label className="mb-1 block text-[11px] text-text-muted">
                    {t('settings.ttsConfig.provider')}
                  </label>
                  <Input
                    className={inputClass}
                    value={form.provider}
                    onChange={(e) => {
                      markTouched('provider');
                      setForm((prev) => ({ ...prev, provider: e.target.value }));
                    }}
                    placeholder="volc"
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-text-muted">
                    {t('settings.ttsConfig.resourceId')}
                    {sourceBadge('resourceId')}
                  </label>
                  <Input
                    className={inputClass}
                    value={form.resourceId}
                    onChange={(e) => {
                      markTouched('resourceId');
                      setForm((prev) => ({ ...prev, resourceId: e.target.value }));
                    }}
                    placeholder={t('settings.ttsConfig.resourceIdPlaceholder')}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-text-muted">
                    {t('settings.ttsConfig.format')}
                  </label>
                  <Select
                    value={form.format}
                    onChange={(v) => {
                      markTouched('format');
                      setForm((prev) => ({ ...prev, format: v }));
                    }}
                    options={FORMATS.map((f) => ({ value: f, label: f }))}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-text-muted">
                    {t('settings.ttsConfig.sampleRate')}
                  </label>
                  <Select
                    value={form.sampleRate}
                    onChange={(v) => {
                      markTouched('sampleRate');
                      setForm((prev) => ({ ...prev, sampleRate: v }));
                    }}
                    options={SAMPLE_RATES.map((r) => ({ value: String(r), label: String(r) }))}
                  />
                </div>
                <div>
                  <label className="mb-1 block text-[11px] text-text-muted">
                    {t('settings.ttsConfig.speechRate')}
                  </label>
                  <Input
                    className={inputClass}
                    value={form.speechRate}
                    inputMode="decimal"
                    onChange={(e) => {
                      markTouched('speechRate');
                      setForm((prev) => ({ ...prev, speechRate: e.target.value }));
                    }}
                    placeholder="1.0"
                  />
                </div>
                <div>
                  {/* 人设联动音色 */}
                  <button
                    type="button"
                    onClick={() => {
                      markTouched('personaVoiceLink');
                      setForm((prev) => ({ ...prev, personaVoiceLink: !prev.personaVoiceLink }));
                    }}
                    className="flex w-full items-start gap-3 rounded-lg border border-border-default bg-surface-overlay px-3 py-2.5 text-left transition-colors hover:border-border-strong"
                  >
                    <Checkbox checked={form.personaVoiceLink} readOnly className="mt-0.5" />
                    <span className="text-xs text-text-secondary">
                      {t('settings.ttsConfig.personaVoiceLink')}
                      <span className="mt-0.5 block text-[11px] text-text-muted">
                        {t('settings.ttsConfig.personaVoiceLinkHelp')}
                      </span>
                    </span>
                  </button>
                </div>
              </div>
            )}
          </section>

          {/* 操作按钮 */}
          <div className="flex flex-wrap items-center gap-2 border-t border-border-subtle pt-4">
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
              {t('settings.ttsConfig.saveAndApply')}
            </SilverButton>
            <SilverButton
              variant="ghost"
              onClick={handleReset}
              disabled={resetMutation.isPending || !hasLoaded}
              className="ml-auto px-4 py-2 text-xs"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              {t('settings.ttsConfig.resetToDefault')}
            </SilverButton>
          </div>
        </div>
      )}
    </GlassCard>
  );
}