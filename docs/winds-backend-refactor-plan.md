# Winds 后端重构方案草案

## 1. 文档目的

本文件基于 [winds-refactor-prd.md](/home/wenxuan/IdeaProjects/winds/docs/winds-refactor-prd.md:1) 输出后端目标架构、数据模型和迁移路径，用于指导后续服务层和数据库重构。

重点解决当前几个问题：

1. 通用 Agent 平台抽象和单一行业产品之间不匹配
2. 聊天会话承担了本应属于诊断任务的职责
3. 数据、知识、参数、报告没有清晰的一等模型
4. 工作流虽然已经专门化，但还挂在通用 Agent 生命周期上

## 2. 当前后端问题

## 2.1 当前一级实体不对

当前数据库和代码中的一级实体主要是：

- `agent`
- `chat_session`
- `chat_message`
- `knowledge_base`
- `document`
- `chunk_bge_m3`

这更像一个通用 AI 平台，而不是诊断系统。

### 实际业务真正的一等对象应是

- 诊断任务
- 信号资产
- 通道
- 参数模板
- 规则模板
- 分析执行记录
- 诊断证据
- 诊断报告

## 2.2 当前工作流挂载点不对

当前业务编排依附于：

- `ChatSession`
- `ChatMessage`
- `AgentWorkflowFactory`

但真实业务触发点应是：

- 创建诊断任务
- 启动任务分析
- 查询分析状态
- 查看分析结果

## 2.3 当前知识容器过于粗糙

`KnowledgeBase` 同时承载：

- Markdown 规则文本
- 参数卡
- MAT 文件
- RAG 检索来源

这会直接导致：

- 参数无法可靠校验
- 数据资产难以独立管理
- RAG 与结构化参数职责混杂

## 3. 目标后端架构

## 3.1 模块划分建议

建议后端按业务域拆成以下模块。

### 3.1.1 diagnosis-task

职责：

- 创建任务
- 更新任务状态
- 绑定数据资产
- 绑定参数模板
- 启动分析
- 保存诊断结论

### 3.1.2 signal-asset

职责：

- 上传与删除 MAT 文件
- 解析文件元信息
- 识别通道
- 管理采样率、单位、设备名
- 提供文件预览和元数据查询

### 3.1.3 parameter-knowledge

职责：

- 维护参数模板
- 维护轴承几何参数
- 维护通道映射
- 维护规则阈值
- 维护经验知识和参考频率

### 3.1.4 analysis-engine

职责：

- FFT 频谱分析
- 包络分析
- 转速分析
- 阶次分析
- 参考频率匹配
- 证据生成

### 3.1.5 diagnosis-report

职责：

- 结构化报告生成
- 版本管理
- 导出
- 修订记录

### 3.1.6 ai-assistant

职责：

- 分析解释
- 证据摘要
- 报告语言润色
- 规则问答

说明：

AI 模块是能力模块，不再是产品主骨架。

## 3.2 分层建议

建议使用清晰的四层结构：

### Interface

- REST Controller
- SSE Controller
- Request / Response DTO

### Application

- Use Case Service
- Task Orchestrator
- Query Service

### Domain

- Aggregate
- Domain Service
- Value Object
- Policy / Rule

### Infrastructure

- MyBatis Mapper
- LLM Client
- Vector Search
- File Storage
- MAT Parser

## 4. 核心领域模型建议

## 4.1 DiagnosisTask

### 职责

承载一次完整诊断工作的主上下文。

### 核心字段建议

- `id`
- `taskCode`
- `title`
- `deviceId`
- `deviceName`
- `scenarioType`
- `status`
- `riskLevel`
- `ownerId`
- `parameterTemplateId`
- `ruleTemplateId`
- `latestRunId`
- `summary`
- `createdAt`
- `updatedAt`

### 状态建议

- `DRAFT`
- `READY`
- `RUNNING`
- `REVIEW`
- `COMPLETED`
- `FAILED`

## 4.2 SignalAsset

### 职责

承载原始 MAT 文件及其解析结果。

### 核心字段建议

- `id`
- `assetCode`
- `filename`
- `assetType`
- `sourceType`
- `deviceName`
- `collectionTime`
- `sampleRate`
- `parseStatus`
- `storagePath`
- `metadata`
- `createdAt`
- `updatedAt`

### assetType 建议

- `VIBRATION`
- `SPEED`
- `MIXED`
- `UNKNOWN`

## 4.3 SignalChannel

### 职责

承载 MAT 中单个通道的识别结果和角色。

### 核心字段建议

- `id`
- `assetId`
- `channelName`
- `channelRole`
- `unit`
- `sampleRate`
- `deviceHint`
- `isPrimary`
- `metadata`

### channelRole 建议

- `VIBRATION`
- `SPEED`
- `TORQUE`
- `UNKNOWN`

## 4.4 ParameterTemplate

### 职责

承载结构化参数模板。

### 核心字段建议

- `id`
- `templateCode`
- `name`
- `templateType`
- `deviceModel`
- `version`
- `status`
- `contentJson`
- `createdAt`
- `updatedAt`

### templateType 建议

- `BEARING`
- `DRIVETRAIN`
- `CHANNEL_MAPPING`
- `ANALYSIS_PROFILE`

## 4.5 DiagnosisRule

### 职责

承载阈值规则、匹配容差和经验规则。

### 核心字段建议

- `id`
- `ruleCode`
- `name`
- `ruleType`
- `version`
- `status`
- `expressionJson`
- `description`

## 4.6 AnalysisRun

### 职责

承载一次分析执行实例。

### 核心字段建议

- `id`
- `taskId`
- `runNo`
- `status`
- `startedAt`
- `finishedAt`
- `triggeredBy`
- `engineVersion`
- `errorCode`
- `errorMessage`
- `snapshotJson`

## 4.7 AnalysisEvidence

### 职责

承载可回溯的证据项。

### 核心字段建议

- `id`
- `runId`
- `evidenceType`
- `sourceAssetId`
- `sourceChannelId`
- `title`
- `score`
- `payloadJson`
- `summary`

### evidenceType 建议

- `SPECTRUM_PEAK`
- `ENVELOPE_PEAK`
- `ORDER_MATCH`
- `REFERENCE_MATCH`
- `STATISTIC_METRIC`
- `RULE_HIT`

## 4.8 DiagnosisConclusion

### 职责

承载任务当前生效的结论。

### 核心字段建议

- `id`
- `taskId`
- `runId`
- `faultLocation`
- `riskLevel`
- `confidence`
- `summary`
- `recommendation`
- `uncertainty`
- `confirmedBy`
- `confirmedAt`

## 4.9 DiagnosisReport

### 职责

承载面向外部输出的正式报告。

### 核心字段建议

- `id`
- `taskId`
- `reportNo`
- `version`
- `status`
- `contentMarkdown`
- `contentJson`
- `exportPdfPath`
- `publishedAt`

## 4.10 AnalysisConversation

### 职责

保留 AI 解释和追问的上下文，但不再作为主容器。

### 核心字段建议

- `id`
- `taskId`
- `runId`
- `conversationType`
- `createdAt`

说明：

- 这是 `ChatSession` 的继承者，但地位从主流程退到辅助功能

## 5. 数据库表设计建议

## 5.1 建议新增的核心表

建议新增：

- `diagnosis_task`
- `task_signal_asset`
- `signal_asset`
- `signal_channel`
- `parameter_template`
- `diagnosis_rule`
- `analysis_run`
- `analysis_evidence`
- `diagnosis_conclusion`
- `diagnosis_report`
- `analysis_conversation`
- `analysis_message`

## 5.2 建议保留但语义调整的表

### `document`

建议过渡期保留，但未来语义应拆分：

- 结构化参数模板不继续依赖它
- MAT 文件逐步迁移到 `signal_asset`
- Markdown 知识可以继续保留在文档体系里

### `chunk_bge_m3`

继续用于 RAG，但只服务于：

- 经验知识检索
- 规则解释
- 报告补充材料

不再承担参数卡主存储职责。

## 5.3 建议退场的主业务表

- `agent`
- `chat_session`
- `chat_message`
- `knowledge_base` 作为万能容器

说明：

这些表不是立即删除，而是逐步从主流程中摘除。

## 6. 旧模型到新模型的映射建议

### 6.1 agent -> analysis strategy / system config

当前 `agent` 中的字段：

- `system_prompt`
- `model`
- `allowed_tools`
- `allowed_kbs`
- `chat_options`

建议迁移去向：

- 模型配置 -> `system_model_config`
- Prompt/生成策略 -> `analysis_strategy`
- 允许知识范围 -> `strategy_knowledge_scope`
- 工具能力 -> 服务内部编排，不对用户开放

### 6.2 chat_session -> diagnosis_task + analysis_conversation

建议拆开：

- 业务上下文 -> `diagnosis_task`
- 追问上下文 -> `analysis_conversation`

不能再让一个会话同时承担任务、执行和结果容器三种职责。

### 6.3 chat_message -> analysis_message / execution_log

建议拆成两类：

- 用户追问和 AI 回答 -> `analysis_message`
- 系统执行状态和节点日志 -> `execution_log` 或 `analysis_run_log`

### 6.4 knowledge_base + document -> parameter_knowledge + signal_asset

建议拆成两条链路：

1. 结构化业务链
   - 参数模板
   - 规则
   - 报告
   - 数据资产
2. 非结构化知识链
   - Markdown 知识
   - 向量切片
   - RAG 检索

## 7. 应用层重构建议

## 7.1 新的核心用例服务

建议至少定义以下应用服务：

- `DiagnosisTaskCommandService`
- `DiagnosisTaskQueryService`
- `SignalAssetCommandService`
- `SignalAssetQueryService`
- `ParameterTemplateService`
- `DiagnosisRuleService`
- `AnalysisExecutionService`
- `DiagnosisReportService`
- `AnalysisAssistantService`

## 7.2 工作流重构建议

当前：

- `AgentWorkflowFactory`
- `WindTurbineBearingWorkflow`

建议演进为：

- `DiagnosisTaskOrchestrator`
- `WindTurbineBearingAnalysisPipeline`

### 新职责边界

`DiagnosisTaskOrchestrator`
- 读取任务上下文
- 校验文件和参数
- 创建分析运行记录
- 驱动各分析步骤
- 持久化结论与报告
- 推送状态流

`WindTurbineBearingAnalysisPipeline`
- 专注领域分析步骤
- 不关心聊天上下文
- 不关心 UI 会话生命周期

## 7.3 SSE 建议

当前 SSE 更偏向聊天流。

建议重构为三种流：

1. `task-status-stream`
   - 任务状态变化
2. `analysis-log-stream`
   - 分析节点执行日志
3. `assistant-stream`
   - AI 解释结果流

## 8. API 重构建议

## 8.1 任务 API

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{taskId}
PATCH  /api/tasks/{taskId}
POST   /api/tasks/{taskId}/start
POST   /api/tasks/{taskId}/rerun
POST   /api/tasks/{taskId}/confirm
```

## 8.2 数据资产 API

```text
GET    /api/assets
POST   /api/assets/upload
GET    /api/assets/{assetId}
PATCH  /api/assets/{assetId}
DELETE /api/assets/{assetId}
GET    /api/assets/{assetId}/channels
```

## 8.3 参数模板 API

```text
GET    /api/parameter-templates
POST   /api/parameter-templates
GET    /api/parameter-templates/{templateId}
PATCH  /api/parameter-templates/{templateId}
```

## 8.4 分析运行 API

```text
GET    /api/tasks/{taskId}/runs
GET    /api/runs/{runId}
GET    /api/runs/{runId}/evidence
GET    /api/runs/{runId}/logs
```

## 8.5 报告 API

```text
GET    /api/reports
GET    /api/reports/{reportId}
POST   /api/reports/{reportId}/export
```

## 8.6 AI 解释 API

```text
POST   /api/tasks/{taskId}/assistant/query
GET    /api/tasks/{taskId}/assistant/messages
```

## 9. 迁移策略建议

## 9.1 原则

不要做一次性大爆炸重构。建议采用并行迁移：

1. 新表先建
2. 新接口先加
3. 新页面先接新接口
4. 旧接口保留一段时间兼容
5. 新流程跑稳后再下线旧表和旧入口

## 9.2 分阶段迁移

### Phase 1: 建新模型，不拆旧模型

完成：

- 新建 `diagnosis_task`、`signal_asset` 等核心表
- 保留 `chat_session`、`knowledge_base`
- 新增任务型 API

### Phase 2: 新前端切主流程

完成：

- 前端主导航切换到任务流
- 新建任务、任务详情改用新 API
- 聊天入口降级

### Phase 3: 工作流切换

完成：

- `WindTurbineBearingWorkflow` 从聊天上下文迁移到任务编排上下文
- 分析结果落到 `analysis_run` / `analysis_evidence`

### Phase 4: 清理旧模型

完成：

- `Agent` 只保留管理员策略用途，或完全退场
- `KnowledgeBase` 不再承载 MAT 文件
- `ChatSession` 不再作为主业务对象

## 10. 兼容方案建议

## 10.1 旧数据兼容

过渡期可以提供一次性迁移脚本：

- 从 `document` 中提取 MAT 文件生成 `signal_asset`
- 从 `chat_session` 生成 `diagnosis_task` 草稿
- 从 `chat_message` 中提取最终报告文字作为历史报告初稿

### 注意

如果旧会话数据质量不高，不建议强行全量迁移为高质量任务数据。可以只迁元信息，不迁证据结构。

## 10.2 代码兼容

建议保留适配层：

- `LegacyChatFacadeAdapter`
- `LegacyKnowledgeBaseAdapter`

用于在过渡期减少前端联调阻力。

## 11. 风险点

### 11.1 参数结构化改造风险

当前参数高度依赖 Markdown 和内置规则。改成结构化模板时，需要补数据清洗和字段定义。

### 11.2 分析结果结构化风险

当前不少结果可能是以文本或消息流形式存在。重构时需要明确哪些证据必须落库，哪些只是展示层文本。

### 11.3 前后端并行改造风险

如果前端先改而后端仍停留在聊天模型，接口层会出现很多临时映射逻辑。需要尽快建立任务型 API，避免前端长期依赖兼容层。

## 12. 第一阶段后端落地顺序

建议按这个顺序做：

1. 建 `diagnosis_task`、`signal_asset`、`signal_channel`
2. 建任务型 Controller 和 Facade
3. 让文件上传支持落到 `signal_asset`
4. 建 `analysis_run`、`analysis_evidence`
5. 把工作流结果从消息流落到结构化结果
6. 最后再处理 `report` 和 `assistant`

## 13. 验收标准

第一阶段后端重构完成后，应满足：

1. 可以不创建 `chat_session` 就发起一次分析任务
2. MAT 文件可作为 `signal_asset` 独立管理
3. 分析执行过程可落 `analysis_run`
4. 核心证据可落 `analysis_evidence`
5. 诊断结论不再只存在于聊天消息正文中
