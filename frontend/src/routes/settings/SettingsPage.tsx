import { GlassCard } from '@/components/ui/glass-card';
import { PageHeader } from '@/components/common/PageHeader';

const modelTiers = [
  { tier: 'FLAGSHIP', description: '旗舰档位（面试官提问）', models: 'GPT-4o / Claude 3.5 Sonnet 级别' },
  { tier: 'STANDARD', description: '标准档位（计划生成、简历解析）', models: 'GPT-4o-mini / Claude 3.5 Haiku 级别' },
];

export function SettingsPage() {
  return (
    <div>
      <PageHeader title="设置" subtitle="模型档位与平台配置" />

      <div className="space-y-6">
        <GlassCard className="p-6">
          <h3 className="mb-4 text-sm font-medium text-text-primary">AI 模型档位</h3>
          <div className="space-y-3">
            {modelTiers.map((m) => (
              <div
                key={m.tier}
                className="flex items-center justify-between border-b border-white/5 pb-3 last:border-0"
              >
                <div>
                  <span className="text-sm font-medium text-silver-200">{m.tier}</span>
                  <p className="mt-0.5 text-xs text-text-muted">{m.description}</p>
                </div>
                <span className="text-xs text-text-secondary">{m.models}</span>
              </div>
            ))}
          </div>
        </GlassCard>

        <GlassCard className="p-6">
          <h3 className="mb-4 text-sm font-medium text-text-primary">环境信息</h3>
          <div className="space-y-2 text-xs text-text-secondary">
            <div className="flex justify-between">
              <span>API 基地址</span>
              <code className="text-silver-300">{import.meta.env.VITE_API_BASE}</code>
            </div>
            <div className="flex justify-between">
              <span>WebSocket 基地址</span>
              <code className="text-silver-300">{import.meta.env.VITE_WS_BASE}</code>
            </div>
            <div className="flex justify-between">
              <span>前端版本</span>
              <span>v0.1.0 · F1</span>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>
  );
}
