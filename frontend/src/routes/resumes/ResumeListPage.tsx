import { useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { useTranslation } from 'react-i18next';
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
import { useEnumLabel } from '@/hooks/useEnumLabel';

export function ResumeListPage() {
  const { t } = useTranslation();
  const enumLabel = useEnumLabel();
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
      toast.error(t('resumes.fileTypeError'));
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
      toast.error(t('resumes.fileRequired'));
      return;
    }
    if (!candidateName.trim()) {
      toast.error(t('resumes.nameRequired'));
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
          toast.success(t('resumes.uploadSuccess'));
          setUploadOpen(false);
        },
        onError: (err: Error) => toast.error(err.message || t('resumes.uploadFailed')),
      },
    );
  };

  const confirmDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(deleteTarget.id, {
      onSuccess: () => {
        toast.success(t('resumes.deleteSuccess'));
        setDeleteTarget(null);
      },
      onError: (err: Error) => toast.error(err.message || t('resumes.deleteFailed')),
    });
  };

  const records = data?.records ?? [];
  const total = data?.total ?? 0;

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('resumes.title')}
        subtitle={t('resumes.subtitle')}
        action={<SilverButton onClick={openUpload}>{t('resumes.upload')}</SilverButton>}
      />

      {/* 搜索栏 */}
      <GlassCard className="p-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[200px]">
            <Label>{t('resumes.candidateName')}</Label>
            <Input
              placeholder={t('resumes.searchNamePlaceholder')}
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            />
          </div>
          <SilverButton variant="ghost" onClick={handleSearch}>
            {t('common.search')}
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
          <ErrorState message={error?.message || t('resumes.loadFailed')} onRetry={() => refetch()} />
        ) : records.length === 0 ? (
          <EmptyState message={t('resumes.noData')} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border-subtle text-text-muted">
                  <th className="px-4 py-3 text-left font-medium">{t('resumes.candidateName')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('resumes.phone')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('resumes.email')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('resumes.parseStatus')}</th>
                  <th className="px-4 py-3 text-left font-medium">{t('resumes.embeddingStatus')}</th>
                  <th className="px-4 py-3 text-right font-medium">{t('resumes.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {records.map((resume) => (
                  <tr
                    key={resume.id}
                    className="border-b border-border-subtle hover:bg-surface-overlay transition-colors"
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
                        label={enumLabel('parseStatus', resume.parseStatus)}
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
                          {t('resumes.view')}
                        </Link>
                        <button
                          onClick={() => setDeleteTarget(resume)}
                          className="text-xs text-danger hover:text-danger/80 transition-colors"
                        >
                          {t('common.delete')}
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
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
            <h3 className="mb-4 text-lg font-semibold text-text-primary">{t('resumes.upload')}</h3>
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
                    ? 'border-silver-300/50 bg-surface-hover'
                    : 'border-border-default bg-surface-overlay hover:border-border-strong')
                }
              >
                <p className="text-sm text-text-secondary">
                  {file ? file.name : t('resumes.dropHint')}
                </p>
                <p className="mt-1 text-xs text-text-muted">{t('resumes.fileFormats')}</p>
                <input
                  id="resume-file-input"
                  type="file"
                  accept=".pdf,.txt"
                  className="hidden"
                  onChange={(e) => handleFile(e.target.files?.[0] ?? null)}
                />
              </div>

              <div>
                <Label>{t('resumes.candidateName')}</Label>
                <Input
                  placeholder={t('resumes.inputNamePlaceholder')}
                  value={candidateName}
                  onChange={(e) => setCandidateName(e.target.value)}
                />
              </div>
              <div>
                <Label>{t('resumes.phoneOptional')}</Label>
                <Input
                  placeholder={t('resumes.phonePlaceholder')}
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
              <div>
                <Label>{t('resumes.emailOptional')}</Label>
                <Input
                  placeholder={t('resumes.emailPlaceholder')}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              {/* 上传进度 */}
              {uploadMutation.isPending && (
                <div className="space-y-1">
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-hover">
                    <div className="h-full w-1/3 animate-stream bg-gradient-to-r from-silver-300 via-silver-100 to-silver-300" />
                  </div>
                  <p className="text-xs text-text-muted">{t('resumes.uploading')}</p>
                </div>
              )}

              <div className="flex justify-end gap-2 pt-2">
                <SilverButton
                  variant="ghost"
                  type="button"
                  onClick={() => setUploadOpen(false)}
                  disabled={uploadMutation.isPending}
                >
                  {t('common.cancel')}
                </SilverButton>
                <SilverButton
                  type="button"
                  onClick={handleUpload}
                  disabled={uploadMutation.isPending}
                >
                  {uploadMutation.isPending ? t('resumes.uploading') : t('resumes.upload')}
                </SilverButton>
              </div>
            </div>
          </GlassCard>
        </div>
      )}

      {/* 删除确认弹窗 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-modal-scrim backdrop-blur-sm p-4">
          <GlassCard className="w-full max-w-sm p-6">
            <h3 className="mb-2 text-lg font-semibold text-text-primary">{t('resumes.deleteConfirmTitle')}</h3>
            <p className="mb-4 text-sm text-text-secondary">
              {t('resumes.deleteConfirmMessage', { name: deleteTarget.candidateName })}
            </p>
            <div className="flex justify-end gap-2">
              <SilverButton
                variant="ghost"
                type="button"
                onClick={() => setDeleteTarget(null)}
              >
                {t('common.cancel')}
              </SilverButton>
              <SilverButton
                variant="danger"
                type="button"
                disabled={deleteMutation.isPending}
                onClick={confirmDelete}
              >
                {deleteMutation.isPending ? t('resumes.deleting') : t('resumes.confirmDelete')}
              </SilverButton>
            </div>
          </GlassCard>
        </div>
      )}
    </div>
  );
}
