import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './layouts/AppLayout';
import LoginPage from './pages/login/LoginPage';
import DashboardPage from './pages/dashboard/DashboardPage';
import ContractListPage from './pages/contracts/ContractListPage';
import ContractUploadPage from './pages/contracts/ContractUploadPage';
import ContractReviewPage from './pages/contracts/ContractReviewPage';
import ApprovalCenterPage from './pages/approvals/ApprovalCenterPage';
import ReviewReportPage from './pages/contracts/ReviewReportPage';
import UserAdminPage from './pages/admin/UserAdminPage';
import ToolConfigPage from './pages/admin/ToolConfigPage';
import ObservabilityPage from './pages/admin/ObservabilityPage';
import KnowledgeQAPage from './pages/knowledge/KnowledgeQAPage';
import KnowledgeManagePage from './pages/knowledge/KnowledgeManagePage';
import AgentTracePage from './pages/contracts/AgentTracePage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/contracts" element={<ContractListPage />} />
          <Route path="/contracts/upload" element={<ContractUploadPage />} />
          <Route path="/contracts/:id" element={<ContractReviewPage />} />
          <Route path="/contracts/:id/report" element={<ReviewReportPage />} />
          <Route path="/contracts/:id/trace" element={<AgentTracePage />} />
          <Route path="/approvals" element={<ApprovalCenterPage />} />
          <Route path="/admin/users" element={<UserAdminPage />} />
          <Route path="/admin/tools" element={<ToolConfigPage />} />
          <Route path="/admin/observability" element={<ObservabilityPage />} />
          <Route path="/knowledge" element={<KnowledgeQAPage />} />
          <Route path="/knowledge/manage" element={<KnowledgeManagePage />} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
