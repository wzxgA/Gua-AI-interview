import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { GlassCard } from '@/components/ui/glass-card';
import { SilverButton } from '@/components/ui/silver-button';
import { Input, Label } from '@/components/ui/input';
import { StatusBadge } from '@/components/ui/status-dot';
import { TableSkeleton } from '@/components/ui/skeleton';
import { Pagination } from '@/components/ui/pagination';
import { PageHeader, EmptyState, ErrorState } from '@/components/common/PageHeader';
import {
  useResumeList,
  useUploadResume,
  useDeleteResume,
} from '@/api/resumes';
import type { ResumeResponse } from '@/types/resume';
import { PAGE_SIZE_DEFAULT } from '@/lib/constants';

export function ResumeListPage() {
  const [query, setQuery] = useState({ candidateName: '', page: 1 });
  const [searchName, setSearchName] = useState('');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<ResumeResponse | null>(null);

  // 上传表单状态
  const [file, setFile] = useState<File | null>(null);
  const [candidateName, setCandidateName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [dragOver, setDragOver] = useState(false);

  const { data, isLoading, isError, error, refetch } = useResumeList({
    page: query.page,
    size: PAGE_SIZE_DEFAULT,
    candidateName: query.candidateName || undefined,
  });

  const uploadMutation = useUploadResume();
  const deleteMutation = useDeleteResume();

  const handleSearch = () => {
    setQuery({ candidateName: searchName, page: 1 });
  };

  const openUpload = () => {
    setFile(null);
    setCandidateName('');
    setPhone('');
    setEmail('');
    setDragOver(false);
    setUploadOpen(true);
  };

  const handleFile = (f: File | null) => {
    if (f && !/\.(pdf|txt)$/i.test(f.name)) {
      toast.error('仅支持 PDF 或 TXT 文件');
      return;
    }
    setFile(f);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(false);
    const f = e.dataTransfer.files?.[0];
    if (f) handleFile(f);
  };

  const handleUpload = () => {
    if (!file) {
      toast.error('请选择简历文件');
      return;
    }
    if (!candidateName.trim()) {
      toast.error('请输入候选人姓名');
      return;
    }
    uploadMutation.mutate(
      {
        file,
        candidateName: candidateName.trim(),
        phone: phone.trim() || undefined,
        email: email.trim() || undefined,
      },
      {
        onSuccess: () => {
          toast.success('简历上传成功');
          setUploadOpen(false);
        },
        onError: (err: Error) => toast.error(err.message || '上传失败'),
      },
    );
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success('简历已删除');
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || '删除失败'),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title="简历管理"
        subtitle="管理候选人简历、解析与向量化状态"
        action={<SilverButton onClick={openUpload}>上传简历</SilverButton>}
      />

      {/* 搜索栏 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[200px]">
            <Label>候选人姓名</Label>
            <Input
              placeholder="搜索候选人姓名"
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            搜索
          </SilverButton>
        </div>
      </GlassCard>

      {/* 表格 */}
      <GlassCard className="overflow-hidden">
        {isLoading ? (
          <div className="p-4">
            <TableSkeleton />
          </div>
        ) : isError ? (
          <ErrorState message={error?.message || '加载失败'} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message="暂无简历数据" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/5 text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">候选人</th>
                  <th className="px-4 py-3 text-left font-medium">手机</th>
                  <th className="px-4 py-3 text-left font-medium">邮箱</th>
                  <th className="px-4 py-3 text-left font-medium">解析状态</th>
                  <th className="px-4 py-3 text-left font-medium">向量状态</th>
                  <th className="px-4 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {records.map((resume) => (
                  <tr
                    key={resume.id}
                    className="border-b border-white/5 hover:bg-white/[0.02] transition-colors"
                  >
                    <td className="px-4 py-3">
                      <Link
                        to={`/resumes/${resume.id}`}
                        className="text-text-primary hover:text-silver-200 transition-colors"
                      >
                        {resume.candidateName}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-text-secondary">{resume.phone || '-'}</td>
                    <td className="px-4 py-3 text-text-secondary">{resume.email || '-'}</td>
                    <td className="px-4 py-3">
                      <StatusBadge
                        status={resume.parseStatus}
                        label={
                          resume.parseStatus === 'FAILED'
                            ? '解析失败'
                            : undefined
                        }
                      />
                    </td>
                    <td className="px-4 py-3">
                      <span className={resume.hasEmbedding ? 'text-success' : 'text-text-muted'}>
                        {resume.hasEmbedding ? '✓' : '✗'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          to={`/resumes/${resume.id}`}
                          className="text-xs text-silver-300 hover:text-silver-100 transition-colors"
                        >
                          查看
                        </Link>
                        <button
                          onClick={() => setDeleteTarget(resume)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </GlassCard>

      {total > 0 && (
        <div className="flex justify-center">
          <Pagination
            current={query.page}
            total={total}
            size={PAGE_SIZE_DEFAULT}
            onPageChange={(page) => setQuery((q) => ({ ...q, page }))}
          />
        </div>
      )}

      {/* 上传弹窗 */}
      {uploadOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">上传简历</h3>
            <div className="space-y-4">
              {/* 拖拽区域 */}
              <div
                onClick={() => document.getElementById('resume-file-input')?.click()}
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragOver(true);
                }}
                onDragLeave={() => setDragOver(false)}
                onDrop={handleDrop}
                className={
                  'cursor-pointer rounded-md border border-dashed p-6 text-center transition-colors ' +
                  (dragOver
                    ? 'border-silver-300/50 bg-white/[0.05]'
                    : 'border-white/10 bg-white/[0.02] hover:border-white/20')
                }
              >
                <p className="text-sm text-text-secondary">
                  {file ? file.name : '点击或拖拽文件到此处上传'}
                </p>
                <p className="mt-1 text-xs text-text-muted">支持 PDF、TXT 格式</p>
                <input
                  id="resume-file-input"
                  type="file"
                  accept=".pdf,.txt"
                  className="hidden"
                  onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
                />
              </div>

              <div>
                <Label>候选人姓名</Label>
                <Input
                  placeholder="请输入候选人姓名"
                  value={candidateName}
                  onChange={(e) => setCandidateName(e.target.value)}
                />
              </div>
              <div>
                <Label>手机（可选）</Label>
                <Input
                  placeholder="请输入手机号"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
              <div>
                <Label>邮箱（可选）</Label>
                <Input
                  placeholder="请输入邮箱"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              {/* 上传进度 */}
              {uploadMutation.isPending && (
                <div className="space-y-1">
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/5">
                    <div className="h-full w-1/3 animate-stream bg-gradient-to-r from-silver-300 via-silver-100 to-silver-300" />
                  </div>
                  <p className="text-xs text-text-muted">上传中...</p>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <SilverButton
                  variant="ghost"
                  type="button"
                  onClick={() => setUploadOpen(false)}
                  disabled={uploadMutation.isPending}
                >
                  取消
                </SilverButton>
                <SilverButton
                  type="button"
                  onClick={handleUpload}
                  disabled={uploadMutation.isPending}
                >
                  {uploadMutation.isPending ? '上传中...' : '上传'}
                </SilverButton>
              </div>
            </div>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">确认删除</h3>
            <p className="mb-4 text-sm text-text-secondary">
              确定要删除候选人「{deleteTarget.candidateName}」的简历吗？此操作不可撤销。
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                取消
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={deleteMutation.isPending}
                onClick={confirmDelete}
              >
                {deleteMutation.isPending ? '删除中...' : '确认删除'}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
