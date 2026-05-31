import { get, post, patch, del, BASE_URL } from "./http.ts";
import type { ChatMessageVO, MessageType } from "../types";

// 类型定义
export interface ChatOptions {
  temperature?: number;
  topP?: number;
  messageLength?: number;
  contextCompression?: ContextCompressionOptions;
  runtimeCache?: RuntimeCacheOptions;
}

export interface ContextCompressionOptions {
  enabled?: boolean;
  model?: string;
  minCharsToCompress?: number;
  maxSummaryChars?: number;
  maxRawPreviewChars?: number;
}

export interface RuntimeCacheOptions {
  sessionMemoryEnabled?: boolean;
  sessionMemoryTtlSeconds?: number;
  toolSummaryEnabled?: boolean;
  toolSummaryTtlSeconds?: number;
}

export type ModelType = "deepseek-chat" | "glm-4.6";

export interface CreateAgentRequest {
  name: string;
  description?: string;
  systemPrompt?: string;
  model: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
}

export interface UpdateAgentRequest {
  name?: string;
  description?: string;
  systemPrompt?: string;
  model?: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
}

export interface CreateAgentResponse {
  agentId: string;
}

export interface AgentVO {
  id: string;
  name: string;
  description?: string;
  systemPrompt?: string;
  model: ModelType;
  allowedTools?: string[];
  allowedKbs?: string[];
  chatOptions?: ChatOptions;
  createdAt?: string;
  updatedAt?: string;
}

export interface GetAgentsResponse {
  agents: AgentVO[];
}

/**
 * 获取所有 agents
 */
export async function getAgents(): Promise<GetAgentsResponse> {
  return get<GetAgentsResponse>("/agents");
}

/**
 * 创建 agent
 */
export async function createAgent(
  request: CreateAgentRequest,
): Promise<CreateAgentResponse> {
  return post<CreateAgentResponse>("/agents", request);
}

/**
 * 删除 agent
 */
export async function deleteAgent(agentId: string): Promise<void> {
  return del<void>(`/agents/${agentId}`);
}

/**
 * 更新 agent
 */
export async function updateAgent(
  agentId: string,
  request: UpdateAgentRequest,
): Promise<void> {
  return patch<void>(`/agents/${agentId}`, request);
}

/**
 * 创建聊天会话
 */
export interface CreateChatSessionRequest {
  agentId: string;
  title?: string;
}

export interface CreateChatSessionResponse {
  chatSessionId: string;
}

export async function createChatSession(
  request: CreateChatSessionRequest,
): Promise<CreateChatSessionResponse> {
  return post<CreateChatSessionResponse>("/chat-sessions", request);
}

/**
 * 聊天会话相关类型和接口
 */
export interface ChatSessionVO {
  id: string;
  agentId: string;
  title?: string;
}

export interface GetChatSessionsResponse {
  chatSessions: ChatSessionVO[];
}

export interface GetChatSessionResponse {
  chatSession: ChatSessionVO;
}

export interface UpdateChatSessionRequest {
  title?: string;
}

/**
 * 获取所有聊天会话
 */
export async function getChatSessions(): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>("/chat-sessions");
}

/**
 * 获取单个聊天会话
 */
export async function getChatSession(
  chatSessionId: string,
): Promise<GetChatSessionResponse> {
  return get<GetChatSessionResponse>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 根据 agentId 获取聊天会话
 */
export async function getChatSessionsByAgentId(
  agentId: string,
): Promise<GetChatSessionsResponse> {
  return get<GetChatSessionsResponse>(`/chat-sessions/agent/${agentId}`);
}

/**
 * 更新聊天会话
 */
export async function updateChatSession(
  chatSessionId: string,
  request: UpdateChatSessionRequest,
): Promise<void> {
  return patch<void>(`/chat-sessions/${chatSessionId}`, request);
}

/**
 * 删除聊天会话
 */
export async function deleteChatSession(chatSessionId: string): Promise<void> {
  return del<void>(`/chat-sessions/${chatSessionId}`);
}

/**
 * 聊天消息相关类型和接口
 */
export interface MetaData {
  [key: string]: unknown;
}

export interface GetChatMessagesResponse {
  chatMessages: ChatMessageVO[];
}

export interface CreateChatMessageRequest {
  agentId: string;
  sessionId: string;
  role: MessageType;
  content: string;
  metadata?: MetaData;
}

export interface CreateChatMessageResponse {
  chatMessageId: string;
}

export interface UpdateChatMessageRequest {
  content?: string;
  metadata?: MetaData;
}

/**
 * 根据 sessionId 获取聊天消息
 */
export async function getChatMessagesBySessionId(
  sessionId: string,
): Promise<GetChatMessagesResponse> {
  return get<GetChatMessagesResponse>(`/chat-messages/session/${sessionId}`);
}

/**
 * 创建聊天消息
 */
export async function createChatMessage(
  request: CreateChatMessageRequest,
): Promise<CreateChatMessageResponse> {
  return post<CreateChatMessageResponse>("/chat-messages", request);
}

/**
 * 更新聊天消息
 */
export async function updateChatMessage(
  chatMessageId: string,
  request: UpdateChatMessageRequest,
): Promise<void> {
  return patch<void>(`/chat-messages/${chatMessageId}`, request);
}

/**
 * 删除聊天消息
 */
export async function deleteChatMessage(chatMessageId: string): Promise<void> {
  return del<void>(`/chat-messages/${chatMessageId}`);
}

/**
 * 知识库相关类型和接口
 */
export interface KnowledgeBaseVO {
  id: string;
  name: string;
  description?: string;
}

export interface CreateKnowledgeBaseRequest {
  name: string;
  description?: string;
}

export interface UpdateKnowledgeBaseRequest {
  name?: string;
  description?: string;
}

export interface GetKnowledgeBasesResponse {
  knowledgeBases: KnowledgeBaseVO[];
}

export interface CreateKnowledgeBaseResponse {
  knowledgeBaseId: string;
}

/**
 * 获取所有知识库
 */
export async function getKnowledgeBases(): Promise<GetKnowledgeBasesResponse> {
  return get<GetKnowledgeBasesResponse>("/knowledge-bases");
}

/**
 * 创建知识库
 */
export async function createKnowledgeBase(
  request: CreateKnowledgeBaseRequest,
): Promise<CreateKnowledgeBaseResponse> {
  return post<CreateKnowledgeBaseResponse>("/knowledge-bases", request);
}

/**
 * 删除知识库
 */
export async function deleteKnowledgeBase(
  knowledgeBaseId: string,
): Promise<void> {
  return del<void>(`/knowledge-bases/${knowledgeBaseId}`);
}

/**
 * 更新知识库
 */
export async function updateKnowledgeBase(
  knowledgeBaseId: string,
  request: UpdateKnowledgeBaseRequest,
): Promise<void> {
  return patch<void>(`/knowledge-bases/${knowledgeBaseId}`, request);
}

/**
 * 文档相关类型和接口
 */
export interface DocumentVO {
  id: string;
  kbId: string;
  filename: string;
  filetype: string;
  size: number;
  documentKind?: string;
  processingStatus?: string;
  parseError?: string;
  signalName?: string;
  sampleRate?: number;
  unit?: string;
  deviceName?: string;
}

export interface GetDocumentsResponse {
  documents: DocumentVO[];
}

export interface CreateDocumentResponse {
  documentId: string;
}

/**
 * 根据知识库 ID 获取文档列表
 */
export async function getDocumentsByKbId(
  kbId: string,
): Promise<GetDocumentsResponse> {
  return get<GetDocumentsResponse>(`/documents/kb/${kbId}`);
}

/**
 * 上传文档
 */
export async function uploadDocument(
  kbId: string,
  file: File,
): Promise<CreateDocumentResponse> {
  const formData = new FormData();
  formData.append("kbId", kbId);
  formData.append("file", file);

  const response = await fetch(`${BASE_URL}/documents/upload`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const apiResponse = await response.json();
  if (apiResponse.code !== 200) {
    throw new Error(apiResponse.message || "上传失败");
  }

  return apiResponse.data;
}

/**
 * 删除文档
 */
export async function deleteDocument(documentId: string): Promise<void> {
  return del<void>(`/documents/${documentId}`);
}

/**
 * 工具相关类型和接口
 */
export type ToolType = "FIXED" | "OPTIONAL";

export interface ToolVO {
  name: string;
  description: string;
  type: ToolType;
}

export interface GetOptionalToolsResponse {
  tools: ToolVO[];
}

/**
 * 获取可选工具列表
 */
export async function getOptionalTools(): Promise<GetOptionalToolsResponse> {
  const tools = await get<ToolVO[]>("/tools");
  return { tools };
}

export interface SignalAssetVO {
  id: string;
  filename: string;
  filetype: string;
  size: number;
  knowledgeBaseId?: string;
  knowledgeBaseName?: string;
  documentKind?: string;
  processingStatus?: string;
  parseError?: string;
  signalName?: string;
  sampleRate?: number;
  unit?: string;
  deviceName?: string;
  availableSignals?: string[];
  defaultSpeedSignalName?: string;
  hasSpeedSignal?: boolean;
  hasVibrationSignal?: boolean;
  updatedAt?: string;
}

export interface GetSignalAssetsResponse {
  assets: SignalAssetVO[];
}

export interface CreateSignalAssetResponse {
  assetId: string;
}

export async function getSignalAssets(): Promise<GetSignalAssetsResponse> {
  return get<GetSignalAssetsResponse>("/signal-assets");
}

export async function uploadSignalAsset(
  file: File,
): Promise<CreateSignalAssetResponse> {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(`${BASE_URL}/signal-assets/upload`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const apiResponse = await response.json();
  if (apiResponse.code !== 200) {
    throw new Error(apiResponse.message || "上传失败");
  }

  return apiResponse.data;
}

export interface ParameterSourceVO {
  id: string;
  name: string;
  description?: string;
}

export interface GetParameterSourcesResponse {
  parameterSources: ParameterSourceVO[];
}

export async function getParameterSources(): Promise<GetParameterSourcesResponse> {
  return get<GetParameterSourcesResponse>("/parameter-sources");
}

export interface ParameterTemplateContent {
  bearingGeometry?: {
    rollingElementCount?: number;
    rollingElementDiameterMm?: number;
    pitchDiameterMm?: number;
    contactAngleDeg?: number;
  };
  thresholds?: {
    crestFactorWarn?: number;
    kurtosisWarn?: number;
    highFrequencyEnergyRatioWarn?: number;
  };
  notes?: string;
}

export interface ParameterTemplateVO {
  id: string;
  name: string;
  deviceModel?: string;
  version: number;
  status: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
  content?: ParameterTemplateContent;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateParameterTemplateRequest {
  name: string;
  deviceModel?: string;
  status?: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
  content?: ParameterTemplateContent;
}

export interface UpdateParameterTemplateRequest {
  name?: string;
  deviceModel?: string;
  status?: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
  content?: ParameterTemplateContent;
}

export interface CreateParameterTemplateResponse {
  templateId: string;
}

export interface GetParameterTemplatesResponse {
  templates: ParameterTemplateVO[];
}

export async function getParameterTemplates(): Promise<GetParameterTemplatesResponse> {
  return get<GetParameterTemplatesResponse>("/parameter-templates");
}

export async function createParameterTemplate(
  request: CreateParameterTemplateRequest,
): Promise<CreateParameterTemplateResponse> {
  return post<CreateParameterTemplateResponse>("/parameter-templates", request);
}

export async function updateParameterTemplate(
  templateId: string,
  request: UpdateParameterTemplateRequest,
): Promise<void> {
  return patch<void>(`/parameter-templates/${templateId}`, request);
}

export interface DiagnosisTaskBasicStats {
  mean?: number;
  rms?: number;
  standardDeviation?: number;
  peakAbs?: number;
  peakToPeak?: number;
  crestFactor?: number;
  kurtosis?: number;
}

export interface DiagnosisTaskPeakSummary {
  frequencyHz: number;
  amplitude: number;
}

export interface DiagnosisTaskSpeedSummary {
  averageRpm: number;
  equivalentFrequencyHz: number;
}

export interface DiagnosisTaskAnalysisSnapshot {
  startedAt?: string;
  finishedAt?: string;
  basicStats?: DiagnosisTaskBasicStats;
  dominantPeaks?: DiagnosisTaskPeakSummary[];
  speedSummary?: DiagnosisTaskSpeedSummary;
  evidence?: string[];
  recommendation?: string;
  conclusion?: string;
}

export interface DiagnosisTaskVO {
  id: string;
  title: string;
  deviceName?: string;
  status: string;
  riskLevel?: string;
  summary?: string;
  symptomHint?: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
  confirmed?: boolean;
  confirmedBy?: string;
  confirmedAt?: string;
  vibrationAsset?: DocumentVO;
  speedAsset?: DocumentVO;
  parameterTemplate?: ParameterTemplateVO;
  parameterSource?: ParameterSourceVO;
  latestAnalysis?: DiagnosisTaskAnalysisSnapshot;
  latestRun?: AnalysisRunVO;
  latestReport?: DiagnosisReportVO;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateDiagnosisTaskRequest {
  title: string;
  deviceName?: string;
  vibrationDocumentId: string;
  speedDocumentId?: string;
  parameterTemplateId?: string;
  parameterKbId?: string;
  symptomHint?: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
}

export interface UpdateDiagnosisTaskRequest {
  title?: string;
  deviceName?: string;
  vibrationDocumentId?: string;
  speedDocumentId?: string;
  parameterTemplateId?: string;
  parameterKbId?: string;
  symptomHint?: string;
  referenceShaft?: string;
  envelopeBandHint?: string;
}

export interface CreateDiagnosisTaskResponse {
  taskId: string;
}

export interface GetDiagnosisTasksResponse {
  tasks: DiagnosisTaskVO[];
}

export interface GetDiagnosisTaskResponse {
  task: DiagnosisTaskVO;
}

export async function getDiagnosisTasks(): Promise<GetDiagnosisTasksResponse> {
  return get<GetDiagnosisTasksResponse>("/diagnosis-tasks");
}

export async function getDiagnosisTask(
  taskId: string,
): Promise<GetDiagnosisTaskResponse> {
  return get<GetDiagnosisTaskResponse>(`/diagnosis-tasks/${taskId}`);
}

export async function createDiagnosisTask(
  request: CreateDiagnosisTaskRequest,
): Promise<CreateDiagnosisTaskResponse> {
  return post<CreateDiagnosisTaskResponse>("/diagnosis-tasks", request);
}

export async function updateDiagnosisTask(
  taskId: string,
  request: UpdateDiagnosisTaskRequest,
): Promise<void> {
  return patch<void>(`/diagnosis-tasks/${taskId}`, request);
}

export async function startDiagnosisTask(
  taskId: string,
): Promise<GetDiagnosisTaskResponse> {
  return post<GetDiagnosisTaskResponse>(`/diagnosis-tasks/${taskId}/start`);
}

export async function confirmDiagnosisTask(
  taskId: string,
  confirmedBy?: string,
): Promise<void> {
  return post<void>(`/diagnosis-tasks/${taskId}/confirm`, { confirmedBy });
}

export interface AnalysisRunMetadata {
  basicStats?: DiagnosisTaskBasicStats;
  evidence?: string[];
  recommendation?: string;
  conclusion?: string;
}

export interface AnalysisRunVO {
  id: string;
  taskId: string;
  runNo: number;
  status: string;
  riskLevel?: string;
  summary?: string;
  metadata?: AnalysisRunMetadata;
  startedAt?: string;
  finishedAt?: string;
  createdAt?: string;
}

export interface AnalysisEvidenceMetadata {
  frequencyHz?: number;
  amplitude?: number;
  source?: string;
}

export interface AnalysisEvidenceVO {
  id: string;
  runId: string;
  evidenceType: string;
  title: string;
  content?: string;
  score?: number;
  metadata?: AnalysisEvidenceMetadata;
  createdAt?: string;
}

export interface GetAnalysisRunsResponse {
  runs: AnalysisRunVO[];
}

export interface GetAnalysisEvidenceResponse {
  evidence: AnalysisEvidenceVO[];
}

export async function getAnalysisRunsByTaskId(
  taskId: string,
): Promise<GetAnalysisRunsResponse> {
  return get<GetAnalysisRunsResponse>(`/diagnosis-tasks/${taskId}/analysis-runs`);
}

export async function getAnalysisEvidenceByRunId(
  runId: string,
): Promise<GetAnalysisEvidenceResponse> {
  return get<GetAnalysisEvidenceResponse>(`/analysis-runs/${runId}/evidence`);
}

export interface DiagnosisReportVO {
  id: string;
  taskId: string;
  runId?: string;
  version: number;
  status: string;
  title: string;
  summary?: string;
  contentMarkdown: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface GetDiagnosisReportsResponse {
  reports: DiagnosisReportVO[];
}

export interface GetDiagnosisReportResponse {
  report: DiagnosisReportVO;
}

export async function getDiagnosisReports(): Promise<GetDiagnosisReportsResponse> {
  return get<GetDiagnosisReportsResponse>("/diagnosis-reports");
}

export async function getDiagnosisReport(
  reportId: string,
): Promise<GetDiagnosisReportResponse> {
  return get<GetDiagnosisReportResponse>(`/diagnosis-reports/${reportId}`);
}
