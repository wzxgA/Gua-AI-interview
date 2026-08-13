import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { useAuth } from '@/contexts/AuthContext';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      toast.error('请输入用户名和密码');
      return;
    }
    setLoading(true);
    try {
      await login(username, password);
      toast.success('登录成功');
      navigate('/');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-text-primary">瓜分Offer</h1>
          <p className="mt-2 text-sm text-text-muted">AI 智能面试 Agent 平台</p>
        </div>
        <GlassCard className="p-8">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-sm font-medium text-text-secondary">
                用户名
              </label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="admin"
                autoFocus
                className="w-full rounded-lg border border-border-default bg-surface-overlay px-3 py-2 text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-sm font-medium text-text-secondary">
                密码
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full rounded-lg border border-border-default bg-surface-overlay px-3 py-2 text-text-primary placeholder:text-text-muted focus:border-accent-primary focus:outline-none"
              />
            </div>
            <SilverButton type="submit" disabled={loading} className="w-full justify-center">
              {loading ? '登录中...' : '登录'}
            </SilverButton>
          </form>
        </GlassCard>
        <p className="mt-4 text-center text-xs text-text-muted">
          初始账号：admin / admin123
        </p>
      </div>
    </div>
  );
}
