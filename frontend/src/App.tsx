import { Routes, Route } from 'react-router-dom';
import { StarfieldBackground } from '@/components/starfield/StarfieldBackground';
import { AppShell } from '@/components/layout/AppShell';
import { DashboardPage } from '@/routes/dashboard/DashboardPage';
import { PositionListPage } from '@/routes/positions/PositionListPage';
import { PositionDetailPage } from '@/routes/positions/PositionDetailPage';
import { QuestionListPage } from '@/routes/questions/QuestionListPage';
import { QuestionImportPage } from '@/routes/questions/QuestionImportPage';
import { ResumeListPage } from '@/routes/resumes/ResumeListPage';
import { ResumeDetailPage } from '@/routes/resumes/ResumeDetailPage';
import { RagDebugPage } from '@/routes/rag/RagDebugPage';
import { SettingsPage } from '@/routes/settings/SettingsPage';

export default function App() {
  return (
    <>
      <StarfieldBackground />
      <Routes>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="positions" element={<PositionListPage />} />
          <Route path="positions/:id" element={<PositionDetailPage />} />
          <Route path="questions" element={<QuestionListPage />} />
          <Route path="questions/import" element={<QuestionImportPage />} />
          <Route path="resumes" element={<ResumeListPage />} />
          <Route path="resumes/:id" element={<ResumeDetailPage />} />
          <Route path="rag" element={<RagDebugPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </>
  );
}
