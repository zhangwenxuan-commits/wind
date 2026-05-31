import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "../layout/Layout.tsx";
import Sidebar from "../layout/Sidebar.tsx";
import SideMenu from "./SideMenu.tsx";
import Content from "../layout/Content.tsx";
import WorkbenchView from "./views/WorkbenchView.tsx";
import DiagnosisTaskListView from "./views/DiagnosisTaskListView.tsx";
import DiagnosisTaskCreateView from "./views/DiagnosisTaskCreateView.tsx";
import DiagnosisTaskDetailView from "./views/DiagnosisTaskDetailView.tsx";
import SignalAssetsView from "./views/SignalAssetsView.tsx";
import ParameterSourcesView from "./views/ParameterSourcesView.tsx";
import ReportsView from "./views/ReportsView.tsx";
import SettingsView from "./views/SettingsView.tsx";
import AgentChatView from "./views/AgentChatView.tsx";
import KnowledgeBaseView from "./views/KnowledgeBaseView.tsx";

export default function JChatMindLayout() {
  return (
    <Layout>
      <Sidebar>
        <SideMenu />
      </Sidebar>
      <Content>
        <Routes>
          <Route path="/" element={<Navigate to="/workbench" replace />} />
          <Route path="/workbench" element={<WorkbenchView />} />
          <Route path="/tasks" element={<DiagnosisTaskListView />} />
          <Route path="/tasks/new" element={<DiagnosisTaskCreateView />} />
          <Route path="/tasks/:taskId" element={<DiagnosisTaskDetailView />} />
          <Route path="/assets" element={<SignalAssetsView />} />
          <Route path="/parameters" element={<ParameterSourcesView />} />
          <Route path="/reports" element={<ReportsView />} />
          <Route path="/settings" element={<SettingsView />} />
          <Route path="/agent" element={<Navigate to="/tasks" replace />} />
          <Route path="/chat" element={<Navigate to="/tasks" replace />} />
          <Route path="/chat/:chatSessionId" element={<Navigate to="/tasks" replace />} />
          <Route path="/knowledge-base" element={<Navigate to="/parameters" replace />} />
          <Route path="/knowledge-base/:knowledgeBaseId" element={<Navigate to="/parameters" replace />} />
          <Route path="/legacy/chat" element={<AgentChatView />} />
          <Route path="/legacy/knowledge-base" element={<KnowledgeBaseView />} />
        </Routes>
      </Content>
    </Layout>
  );
}
