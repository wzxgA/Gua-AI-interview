import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Skeleton } from '@/components/ui/skeleton';
import { getAccessInfo, verifyPassword, type AccessInfo } from '@/api/access';
import { useEnumLabel } from '@/hooks/useEnumLabel';
import { useUrlLanguageInit } from '@/hooks/useUrlLanguageInit';
import { LanguageSwitcher } from '@/components/ui/LanguageSwitcher';

/** 候选人进入页：展示面试信息 + 输入访问密码（无需登录）。 */
export function CandidateAccessPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
  useUrlLanguageInit();
  const { accessToken } = useParams();
  const navigate = useNavigate();
  const [info, setInfo] = useState<AccessInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    getAccessInfo(accessToken)
      .then((d) => {
        if (!cancelled) {
          setInfo(d);
          // 面试级防作弊配置传给面试间使用
          sessionStorage.setItem('guestProctor', JSON.stringify(d.proctor ?? { tabSwitch: false, gaze: false }));
        }
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : t('candidate.invalidLink'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, t]);

  const handleEnter = async () => {
    if (!accessToken) return;
    if (info?.requirePassword && !password) {
      toast.error(t('candidate.passwordRequired'));
      return;
    }
    setVerifying(true);
    try {
      const res = await verifyPassword(accessToken, info?.requirePassword ? password : '');
      sessionStorage.setItem('guestToken', res.guestToken);
      sessionStorage.setItem('guestSessionId', String(res.sessionId));
      navigate(`/i/${accessToken}/room`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('candidate.enterFailed'));
    } finally {
      setVerifying(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="mb-6">
          <div className="flex justify-end">
            <LanguageSwitcher />
          </div>
          <div className="text-center">
            <h1 className="text-2xl font-bold text-text-primary">{t('common.appName')}</h1>
            <p className="mt-2 text-sm text-text-muted">{t('candidate.slogan')}</p>
          </div>
        </div>

        {loading ? (
          <GlassCard className="p-8">
            <Skeleton className="h-36 w-full" />
          </GlassCard>
        ) : error || !info ? (
          <GlassCard className="p-8 text-center">
            <p className="text-sm text-danger">{error ?? t('candidate.invalidLinkShort')}</p>
            <div className="mt-6 flex justify-center gap-3">
              <SilverButton variant="ghost" onClick={() => window.location.reload()}>
                {t('candidate.reload')}
              </SilverButton>
            </div>
          </GlassCard>
        ) : (
          <GlassCard className="p-8">
            <div className="mb-6 text-center">
              <p className="text-xl font-semibold text-text-primary">{info.candidateName}</p>
              <p className="mt-1 text-sm text-text-muted">{info.position}</p>
              <span className="mt-3 inline-block rounded-full border border-border-default bg-surface-hover px-3 py-1 text-xs text-text-secondary">
                {enumLabel('sessionStatus', info.status, info.status)}
              </span>
            </div>

            {info.requirePassword && (
              <div className="mb-6">
                <label className="mb-2 block text-sm font-medium text-text-secondary">
                  {t('candidate.passwordLabel')}
                </label>
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') handleEnter();
                  }}
                  placeholder={t('candidate.passwordPlaceholder')}
                  autoFocus
                  className="w-full rounded-lg border border-border-default bg-surface-overlay px-3 py-2 text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
                />
              </div>
            )}

            <SilverButton className="w-full" onClick={handleEnter} disabled={verifying}>
              {verifying ? t('candidate.verifying') : t('candidate.enter')}
            </SilverButton>
          </GlassCard>
        )}
      </div>
    </div>
  );
}
