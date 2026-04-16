# 风机轴承智能诊断项目面试题库（可扩充）

目标：用于项目面试高压追问训练。  
结构：先题目，后标准答案。  
规则：题号固定，后续新增按编号递增，标准答案统一放文档末尾。

---

## 零、源码状态流转先读（先看这个）

### 0.1 关键类与方法语义（谁驱动谁）

- `JChatMindFactory.create(...)`：组装一次诊断运行时，加载会话状态、工具、知识库、Workflow 实例。
- `SessionRuntimeStateStore.loadOrCreate(...)`：加载或创建会话运行态（支持内存缓存和会话元数据持久化）。
- `AgentWorkflowFactory.create(...)`：创建 `WindTurbineBearingWorkflow`，把运行态里的工作区绑定到本次流程。
- `JChatMind.run()`：外层主循环，反复执行“拿节点计划 -> 节点执行 -> 状态推进”，直到完成或达到上限。
- `JChatMind.step(plan)`：单节点执行入口，顺序是“是否直接生成报告 -> think -> execute”。
- `JChatMind.think(plan)`：调用模型，产出助手消息和可能的工具调用计划。
- `JChatMind.execute(plan)`：执行工具调用，处理工具输出压缩，回写上下文，再通知 Workflow 消化结果。
- `WindTurbineBearingWorkflow.nextPlan()`：状态机出下一节点计划（节点提示词、允许工具集合、状态文案）。
- `WindTurbineBearingWorkflow.onToolResponses(...)`：把工具结果写入工作区事实字段，并尝试推进状态。
- `WindTurbineBearingWorkflow.onAssistantResponse(...)`：处理“无工具调用”分支，决定推进、等待用户、或结束。
- `WindTurbineBearingWorkflow.advanceStateIfPossible()`：状态推进核心函数，依据工作区字段连续推进。
- `DiagnosisReportComposer.compose(...)`：报告节点的后端确定性兜底报告生成器。

### 0.2 状态机全流程（从 INIT 到 DONE）

状态枚举：`INIT -> SELECT_DOCUMENT -> LOAD_PARAMETER_CONTEXT -> RUN_BASE_ANALYSIS -> DECIDE_ADVANCED_ANALYSIS -> RUN_ADVANCED_ANALYSIS -> GENERATE_DIAGNOSIS -> DONE`

| 当前状态 | 进入条件 | 前进条件（满足才推进） | 关键写入字段 |
| --- | --- | --- | --- |
| `INIT` | 新建工作区默认状态 | 无条件推进到 `SELECT_DOCUMENT` | `currentState` |
| `SELECT_DOCUMENT` | 刚进入流程或待选文档 | `selectedDocumentId` 非空 | `selectedDocumentId`、`selectedDocumentName`、`candidateDocuments` |
| `LOAD_PARAMETER_CONTEXT` | 已选主振动文档 | `availableKbs` 为空，或 `parameterContextLoaded=true` | `parameterContextLoaded` |
| `RUN_BASE_ANALYSIS` | 参数上下文已就绪或可跳过 | `baseAnalysisCompleted=true` | `crestFactor`、`kurtosis`、`highFrequencyRatio`、`baseAnalysisCompleted` |
| `DECIDE_ADVANCED_ANALYSIS` | 基础分析完成 | 每次都会推进：按门控结果去高级分析或直接报告 | `advancedAnalysisRequired`、`lastDecisionReason` |
| `RUN_ADVANCED_ANALYSIS` | 门控判定需要高级证据 | `canGenerateDiagnosisAfterAdvancedAnalysis()` 为真 | `advancedAnalysisCompleted`、`speedAnalysisCompleted`、`orderSpectrumCompleted`、`referenceMatchEvaluated`、`bearingFrequenciesCalculated` |
| `GENERATE_DIAGNOSIS` | 高级分析充分或无需高级分析 | 报告输出后推进到 `DONE` | 报告内容、最终摘要 |
| `DONE` | 已结束 | 终态 | `finished=true` |

### 0.3 前进条件细化（最容易被问的点）

- `SELECT_DOCUMENT -> LOAD_PARAMETER_CONTEXT`  
条件：`selectedDocumentId` 必须有值。  
来源：自动选文档或用户澄清后命中候选。

- `LOAD_PARAMETER_CONTEXT -> RUN_BASE_ANALYSIS`  
条件：知识库为空可直接过；否则要 `parameterContextLoaded=true`。

- `RUN_BASE_ANALYSIS -> DECIDE_ADVANCED_ANALYSIS`  
条件：`baseAnalysisCompleted=true`。  
该字段通常在 `analyzeVibrationSpectrum` 工具结果被解析后置位。

- `DECIDE_ADVANCED_ANALYSIS` 的分流逻辑  
`crestFactor >= 4.5` 或 `kurtosis >= 4.5` 或 `highFrequencyRatio >= 0.45`：进入 `RUN_ADVANCED_ANALYSIS`。  
否则：直接 `GENERATE_DIAGNOSIS`。

- `RUN_ADVANCED_ANALYSIS -> GENERATE_DIAGNOSIS`  
先看一个硬条件：如果存在速度文档且 `speedAnalysisCompleted=false`，不能前进。  
再看充分条件（任一满足）：  
`advancedAnalysisCompleted=true` 或  
`speedAnalysisCompleted=true` 或  
`bearingFrequenciesCalculated=true` 或  
`orderSpectrumCompleted=true` 或  
`referenceMatchEvaluated=true`。

- 高级节点“无工具调用”特殊分支  
如果当前状态是 `RUN_ADVANCED_ANALYSIS` 且本轮无工具调用，会强制转报告节点，避免空转。

- 报告节点结束  
`GENERATE_DIAGNOSIS` 节点若无工具调用，`onAssistantResponse` 会把状态置为 `DONE`。

### 0.4 全局停止条件（防死循环）

- 节点级上限：`workflowStepCount >= MAX_NODE_STEPS`（Workflow 内部强制 `DONE`）。
- 回合级上限：`JChatMind.run()` 的 `MAX_STEPS`（主循环保险丝）。
- 结束判定：`workflow.isFinished()` 或计划状态为 `DONE`。

### 0.5 状态持久化与排障看板（卡住时先看这些）

排障先看这些字段：

- `currentState`：当前节点。
- `selectedDocumentId`：是否完成选文档。
- `parameterContextLoaded`：是否完成参数上下文加载。
- `baseAnalysisCompleted`：是否完成基础频谱分析。
- `advancedAnalysisRequired`：是否判定需要高级分析。
- `selectedSpeedDocumentId`、`speedAnalysisCompleted`：速度路径是否卡住。
- `orderSpectrumCompleted`、`referenceMatchEvaluated`、`bearingFrequenciesCalculated`：高级证据是否达标。
- `awaitingUserInput`：是否在等用户确认。
- `workflowStepCount`：是否接近节点上限。
- `lastDecisionReason`、`evidenceNotes`：状态分流依据和证据轨迹。

---

## 一、基础题库

### A. 项目与架构基础

- **B01**：请用一句话介绍这个项目解决了什么问题？
- **B02**：这个系统的输入和输出分别是什么？
- **B03**：为什么采用“Workflow + 节点内 Agent”，而不是一个大 Agent 自由规划？
- **B04**：Workflow 主干节点有哪些？每个节点目标是什么？
- **B05**：Workflow 的状态流转规则是怎么设计的？
- **B06**：什么情况下进入高级分析节点？

### B. 节点 Agent 与提示词

- **B07**：节点 Agent 是如何创建的？创建时注入了哪些约束？
- **B08**：Workflow 如何影响节点 Agent 的行为边界？
- **B09**：每个节点的提示词由哪些部分组成？
- **B10**：节点 Agent 的职责边界是什么？哪些事情不能做？

### C. 工具链与调用治理

- **B11**：振动分析工具的入参和出参规范怎么定义？
- **B12**：Agent 如何根据上下文决定调用哪个工具和传入什么参数？
- **B13**：上线后有没有出现错调工具、传参错误？你是怎么解决的？
- **B14**：频谱分析、包络分析、特征频率计算是自研还是第三方？如何保证可靠性？

### D. RAG 与知识检索

- **B15**：知识库包含哪些类型的数据？
- **B16**：这些数据是如何预处理和切片的？
- **B17**：使用了什么向量模型，如何落库 pgvector？
- **B18**：召回时如何保证与当前型号、工况、振动证据强匹配？

### E. 压缩、缓存与性能

- **B19**：工具结果摘要压缩机制怎么实现？
- **B20**：压缩时如何保证关键故障特征不丢失？
- **B21**：会话运行缓存和工作记忆缓存分别缓存什么？
- **B22**：缓存更新、淘汰和防脏策略是什么？
- **B23**：做了哪些指标埋点？压测怎么做？
- **B24**：优化后最关键的性能收益指标有哪些？

### F. 源码级链路

- **B25**：一次请求从入口到报告输出的源码执行链路是什么？
- **B26**：节点计划在哪里生成？工具白名单在哪里生效？
- **B27**：工具调用结果在哪里被压缩并回写上下文？
- **B28**：最终报告是模型生成还是程序兜底生成？触发条件是什么？

---

## 二、扩展题库

### A. 流程控制与容错

- **E01**：你的流程是固定串行，还是支持动态分支、重试和回退？
- **E02**：如果特征频率匹配失败，如何回到上游节点重算？
- **E03**：你如何避免无限循环或状态抖动？
- **E04**：节点工具调用失败时，节点级和流程级如何降级？

### B. 状态管理与上下文工程

- **E05**：如何在多轮诊断中保持状态连续，同时避免上下文膨胀？
- **E06**：为什么只保留少量最近原始消息，不保留全量历史？
- **E07**：`Workspace Snapshot` 和 `Conversation Digest` 各负责什么？
- **E08**：会话状态如何跨轮次持久化？进程重启后如何恢复？

### C. 工具执行安全与治理

- **E09**：如何实现工具白名单、防越权和工具名不一致兜底？
- **E10**：为什么执行阶段还要补齐工具集合？是否有越权风险？
- **E11**：第三方算法超时或报错时如何处理？
- **E12**：如何保证工具输出可被主模型稳定消费？

### D. RAG 深挖

- **E13**：向量服务不可用时为什么要关键词回退？
- **E14**：pgvector 相似度检索 SQL 是怎么写的？返回条数怎么定？
- **E15**：为什么采用“先过滤，再召回，再重排”？
- **E16**：你如何评估召回质量？哪些指标最有说服力？

### E. 诊断算法与规则

- **E17**：基础频谱如何提取主峰、谐波和高频能量比？
- **E18**：包络分析为什么采用带通加解析信号？
- **E19**：高级分析是否总是必须？如何平衡时延和效果？
- **E20**：参考频率匹配为什么同时看相对频率误差和阶次误差？

### F. 可观测性与工程化

- **E21**：指标埋点如何形成性能调优闭环？
- **E22**：压测脚本如何区分预热和实测，如何看分位数？
- **E23**：请给两个线上故障复盘案例。
- **E24**：如果再迭代一个版本，你优先改哪三件事？

---

## 三、可扩充规则

- 基础题新增编号：`B29`、`B30`……
- 扩展题新增编号：`E25`、`E26`……
- 每个题目只问一个核心点。
- 建议答案结构：结论 + 实现 + 失败场景 + 指标结果。

---

## 四、标准答案（文档末尾）

### 基础题标准答案

**B01 标准答案**  
这是一个面向风机轴承场景的振动智能诊断系统，用流程编排和节点 Agent 把诊断拆成可控多阶段，并把检索、工具计算和诊断解释串成可审计链路。

**B02 标准答案**  
输入是 MAT 振动和转速信号、设备参数和诊断规则知识。输出是结构化诊断报告，包含结论、证据、风险等级、建议动作和不确定性说明。

**B03 标准答案**  
工业诊断要求可控、可回放、可审计。大 Agent 自由规划容易越权和漂移；Workflow 固定边界，节点 Agent 只做局部决策，稳定性更高。  
源码锚点：`agent/workflow/AgentWorkflow.java`、`agent/workflow/vibration/WindTurbineBearingWorkflow.java`。

**B04 标准答案**  
主干节点是：选文档、载入参数、基础频谱、是否进入高级分析、高级分析、生成报告。每个节点都有目标和允许工具集合。  
源码锚点：`WindTurbineBearingWorkflow.nextPlan()`。

**B05 标准答案**  
状态推进由状态机实现，依赖工作区事实而不是模型口头描述。每轮通过 `advanceStateIfPossible()` 推进。  
源码锚点：`DiagnosisWorkflowState`、`advanceStateIfPossible()`。

**B06 标准答案**  
基础频谱后按阈值门控。峰值因子、峭度、高频能量比任一超阈值就进入高级分析。  
源码锚点：`evaluateAdvancedNeed()`。

**B07 标准答案**  
节点 Agent 由运行时工厂创建，注入模型、节点计划、工具集合和会话上下文，不是裸模型调用。  
源码锚点：`JChatMindFactory.create()`、`AgentWorkflowFactory.create()`。

**B08 标准答案**  
Workflow 通过 `WorkflowStepPlan` 下发节点状态、节点提示词和工具白名单，节点 Agent 无法直接修改流程图。  
源码锚点：`WorkflowStepPlan`、`JChatMind.resolveToolCallbacks()`。

**B09 标准答案**  
提示词由角色说明、节点指令、工作区快照、对话摘要和上下文策略组成，目标是减少上下文膨胀和推理漂移。  
源码锚点：`ContextAssembler.buildSystemPrompt()`。

**B10 标准答案**  
节点 Agent 只负责本节点工具规划和证据组织，不负责全局编排，也不提前给最终结论。是否结束由 Workflow 决定。  
源码锚点：`JChatMind.step()`、`workflow.onAssistantResponse()`。

**B11 标准答案**  
工具接口统一做入参校验和结构化输出。频谱、包络、速度、阶次、参考频率匹配都走统一工具层。  
源码锚点：`agent/tools/VibrationAnalysisTool.java`、`service/vibration/VibrationModels.java`。

**B12 标准答案**  
Agent 先看节点目标和工作区状态，再在白名单内选工具。参数优先来自已选文档和会话状态，再补知识检索结果。  
源码锚点：`WorkflowStepPlan.allowedToolNames`、`WindTurbineBearingWorkflow` 各 `handle*Payload`。

**B13 标准答案**  
出现过工具名不一致和参数缺失。解决方式是节点白名单、执行阶段名称归一与补齐、工具入参硬校验三层治理。  
源码锚点：`resolveExecutionToolCallbacks()`、`matchesToolName()`。

**B14 标准答案**  
诊断流程和规则是自研，底层数值计算使用成熟库。FFT 和复数运算使用 Apache Commons Math，保证计算稳定性。  
源码锚点：`VibrationAnalysisServiceImpl` 中 `FastFourierTransformer`。

**B15 标准答案**  
知识库包含参数文档、规则文档、通道映射、参考频率说明文档，索引粒度是 Markdown 分段。

**B16 标准答案**  
预处理流程是文档解析、按标题分段、过滤低质量段、构造元数据后入库，降低噪声召回。  
源码锚点：`RagServiceImpl.indexMarkdownDocument()`。

**B17 标准答案**  
默认向量模型是 `bge-m3`。向量写入 pgvector 字段，检索按距离排序取前 K。  
源码锚点：`application.yaml`、`RagServiceImpl`、`mapper/ChunkBgeM3Mapper.xml`。

**B18 标准答案**  
先按知识库范围和上下文过滤，再做语义召回，再结合参考频率匹配结果重排，保证证据强相关。

**B19 标准答案**  
压缩是双通道：优先模型压缩，失败回退确定性压缩，并记录压缩前后载荷长度指标。  
源码锚点：`DefaultToolResultProcessor.process()`、`summarizeWithFallback()`。

**B20 标准答案**  
通过压缩提示词明确保留数字、标识、状态、错误和约束，再配合头尾保留兜底策略，避免关键证据丢失。  
源码锚点：`buildSystemPrompt()`、`deterministicCompact()`。

**B21 标准答案**  
会话运行缓存存会话状态、工作区和摘要；工具摘要缓存存同工具同原始内容的压缩结果。  
源码锚点：`SessionRuntimeStateStore`、`DiagnosisWorkspaceCache`、`ToolSummaryCache`。

**B22 标准答案**  
更新策略是成功后写回，淘汰策略是 TTL。防脏关键是缓存键包含版本、模型、工具名和原文哈希。  
源码锚点：`ToolSummaryCache.ToolSummaryCacheKey.of()`。

**B23 标准答案**  
埋点覆盖耗时、缓存命中、上下文大小、工具载荷和 token。压测脚本支持预热、实测和分位统计。  
源码锚点：`metrics/AgentMetrics.java`、`scripts/benchmark-chat-agent.ps1`。

**B24 标准答案**  
核心收益看三项：多轮时延下降、缓存命中率提升、token 消耗下降。面试时给真实压测数据。

**B25 标准答案**  
链路是：载入会话状态 -> 构建 Workflow -> 生成节点计划 -> 思考 -> 工具执行 -> 工具结果压缩 -> 更新状态 -> 下一节点。  
源码锚点：`JChatMindFactory.create()`、`JChatMind.run()`。

**B26 标准答案**  
节点计划在 `WindTurbineBearingWorkflow.nextPlan()` 生成，白名单通过 `allowedToolNames` 下发并在 `resolveToolCallbacks()` 生效。

**B27 标准答案**  
工具输出在 `DefaultToolResultProcessor` 压缩，处理后在 `JChatMind.execute()` 回写到当前上下文和运行态。

**B28 标准答案**  
报告支持确定性兜底生成。进入 `GENERATE_DIAGNOSIS` 时触发 `DiagnosisReportComposer`，保证可控输出。  
源码锚点：`emitDeterministicDiagnosisReportIfNeeded()`。

### 扩展题标准答案

**E01 标准答案**  
不是纯串行，而是固定主干加条件分支加有界重试的状态机。

**E02 标准答案**  
匹配失败时会继续高级节点补证据，必要时回补参数和转速信息，再推进到报告节点。当前实现偏前向补证据。

**E03 标准答案**  
防循环有两层：状态推进前置条件和最大步数限制。超过上限会收敛到完成态。  
源码锚点：`MAX_NODE_STEPS`、`MAX_STEPS`。

**E04 标准答案**  
节点失败先做可恢复处理并尝试可行路径；流程失败输出保守结论和不确定性声明，不做强结论。

**E05 标准答案**  
上下文拆成结构化状态加摘要加少量原始消息尾部，不做全量历史重放。

**E06 标准答案**  
全量历史会造成 token 膨胀和噪声累积。少量原始消息只保语言连续，事实以状态和摘要为准。  
源码锚点：`MAX_RECENT_RAW_MESSAGES`。

**E07 标准答案**  
`Workspace Snapshot` 管当前事实状态，`Conversation Digest` 管用户意图和会话要点，两者组合替代长历史。

**E08 标准答案**  
会话状态写入会话元数据并带 TTL 缓存，重启后可从数据库恢复。  
源码锚点：`SessionRuntimeStateStore.loadFromSessionMetadata()`。

**E09 标准答案**  
三层治理：节点白名单、执行校验、名称归一匹配，综合防越权和错调。

**E10 标准答案**  
执行补齐是为了解决模型返回名称别名导致找不到工具。最终仍受当前节点约束，不是放开权限。

**E11 标准答案**  
第三方异常会在工具层捕获并转成可解释错误，流程继续走降级或补证据，不打断主链路。

**E12 标准答案**  
稳定性来自统一输出结构、统一压缩策略和统一回写链路，让主模型消费固定格式。

**E13 标准答案**  
向量服务不可用时关键词回退能保证系统可用性，避免检索链路整体中断。  
源码锚点：`fallbackMarkdownSearch()`。

**E14 标准答案**  
SQL 用 `embedding <-> query_vector` 距离排序，默认取前 3。K 值结合文档粒度和噪声调参。  
源码锚点：`mapper/ChunkBgeM3Mapper.xml`。

**E15 标准答案**  
先过滤再召回再重排可以减少误召回，把业务约束重新拉回语义结果。

**E16 标准答案**  
至少看召回命中率、误召回率、人工修正率，并给优化前后对比。

**E17 标准答案**  
基础频谱流程是去均值、加窗、FFT、局部峰值检测、谐波计数和高频能量比计算。  
源码锚点：`computeSpectrum()`、`findDominantPeaks()`。

**E18 标准答案**  
带通加解析信号可把冲击特征从载频中解调出来，对早期故障更敏感。  
源码锚点：`buildEnvelope()`、`buildAnalyticSpectrum()`。

**E19 标准答案**  
高级分析不是总开，只有门控触发才进入，平衡时延和证据深度。  
源码锚点：`evaluateAdvancedNeed()`。

**E20 标准答案**  
只看频率误差在变速工况下不稳，加入阶次误差可增强鲁棒性。  
源码锚点：`matchReferenceEntry()`。

**E21 标准答案**  
核心指标覆盖模型思考耗时、工具执行耗时、上下文处理耗时、缓存命中和 token，用于定位瓶颈。

**E22 标准答案**  
压测脚本先预热再实测，输出平均值和 P50、P95、P99，并支持 Actuator 指标快照。

**E23 标准答案**  
建议准备两个复盘：工具名不一致导致执行失败；向量服务不可用导致检索失败及回退方案。

**E24 标准答案**  
下一版优先三件事：离线评测集和自动回归、节点输入输出结构化强校验、动态编排回退图和审计增强。

---

## 五、深挖题库（源码级追问）

### A. Workflow 与状态机深挖

- **D01**：`nextPlan()` 每次调用前后，状态推进顺序是什么？为什么先推进再出计划？
- **D02**：`advanceStateIfPossible()` 为什么用 `do-while` 连续推进，而不是一次只推进一步？
- **D03**：`MAX_NODE_STEPS` 和 `MAX_STEPS` 两层上限分别防什么风险？
- **D04**：`onAssistantResponse()` 在“无工具调用”场景下的分支语义是什么？
- **D05**：为什么高级分析节点“无工具调用”会强制转报告节点？
- **D06**：`speedPathEnabled` 为 false 时，哪些状态会被重置？为什么要显式重置？
- **D07**：如果用户在多文档候选里给出模糊回答，系统如何处理歧义？
- **D08**：你如何解释“当前实现是前向补证据，不是任意回滚”这件事的取舍？

### B. 节点 Agent 与 Prompt 深挖

- **D09**：`ContextAssembler.buildSystemPrompt()` 为什么把工作区快照放在摘要之前？
- **D10**：为什么上下文策略强调“workspace/digest 优先于历史消息”？
- **D11**：`MAX_RECENT_RAW_MESSAGES` 取 4 的依据是什么？如何调这个参数？
- **D12**：你如何防止提示词注入导致越权工具调用？
- **D13**：节点提示词中“Do not diagnose faults at this node”这类约束是否真的有效？如何双重保证？
- **D14**：如果模型仍然给出跨节点结论，编排层怎么兜底？
- **D15**：为什么报告节点禁止工具调用？
- **D16**：如果要把节点提示词模板化配置化，你会怎么拆？

### C. 工具执行链路深挖

- **D17**：`resolveExecutionToolCallbacks()` 为什么会补齐回调集合？解决了什么线上问题？
- **D18**：名称归一匹配（去符号、小写、前缀匹配）可能带来什么误判风险？
- **D19**：工具入参校验放在工具方法里，而不是只靠模型约束，理由是什么？
- **D20**：工具异常返回字符串错误而不是抛异常中断，工程利弊是什么？
- **D21**：工具输出压缩后写回 memory，会不会影响可追溯性？
- **D22**：如何定位一次错误是“工具失败”还是“模型解释失败”？
- **D23**：频谱计算里为何先去均值再加窗再 FFT？
- **D24**：包络分析里带通参数不合理时会有什么后果？你如何防护？
- **D25**：参考频率匹配为什么采用“相对频率容差 OR 阶次容差”？
- **D26**：如果 observed peaks 很少，匹配结论如何避免过拟合？

### D. RAG 与检索深挖

- **D27**：为什么 `RagServiceImpl` 在检索前可选自动索引（auto-index-on-search）？
- **D28**：向量检索失败后关键词回退会不会引入语义偏差？如何降低风险？
- **D29**：为什么当前索引文本主要用 section 标题而不是整段内容向量？
- **D30**：`similaritySearch` topK=3 的经验依据是什么？什么时候要调大？
- **D31**：如何防止不同知识库之间“脏召回”？
- **D32**：如果文档版本更新，旧向量如何失效或重建？
- **D33**：pgvector 距离检索的性能瓶颈通常在哪里？
- **D34**：如何构建离线评测集评估召回准确率？

### E. 缓存与一致性深挖

- **D35**：`SessionRuntimeStateStore` 为什么既有数据库持久化又有 TTL 内存缓存？
- **D36**：状态写回失败时会有什么一致性风险？如何兜底？
- **D37**：`ToolSummaryCacheKey` 里为什么要包含 version、model、rawHash？
- **D38**：工具摘要缓存是否需要区分租户或场景维度？
- **D39**：TTL 缓存如何处理并发写入覆盖问题？
- **D40**：什么时候应该主动 `invalidate` 而不是等 TTL 过期？

### F. 观测与压测深挖

- **D41**：你最看重的三个时延指标是什么？为什么？
- **D42**：如何用 token 指标区分“压缩收益”与“提示词膨胀”？
- **D43**：缓存命中率高但时延仍高，可能原因有哪些？
- **D44**：P95 下降但平均值不变，说明了什么？
- **D45**：如何做一次可复现的性能对比实验？
- **D46**：压测时复用会话与不复用会话分别验证什么能力？

### G. 架构取舍与演进深挖

- **D47**：为什么当前不开放“任意动态节点生成”？
- **D48**：如果要支持真正回滚节点，你会改哪三处核心代码？
- **D49**：如何把规则阈值（如 4.5/0.45）做成可运营策略？
- **D50**：如果目标是工业上线，你会优先补齐哪些非功能能力？

---

## 六、深挖题标准答案（文档末尾）

**D01 标准答案**  
先推进状态再出计划，能确保计划与最新工作区事实一致，避免“计划滞后一个状态”。

**D02 标准答案**  
`do-while` 连续推进可以一次跨过无需停留的中间状态，减少无效轮次和模型调用。

**D03 标准答案**  
节点级上限防节点内磨损，回合级上限防全局死循环，两层是不同粒度保护。

**D04 标准答案**  
无工具调用时会根据当前状态判断：结束、转报告、推进下一状态、或等待用户补充信息。

**D05 标准答案**  
高级节点若无新增工具调用，继续停留没有收益，直接转报告可保证流程收敛。

**D06 标准答案**  
会重置速度路径相关字段，避免关闭速度路径后仍使用旧速度证据造成脏状态。

**D07 标准答案**  
优先索引提示匹配，其次文件名/信号名匹配；仍歧义则保留待确认并提示用户澄清。

**D08 标准答案**  
前向补证据更稳定、实现复杂度低；任意回滚更灵活但状态一致性和可审计成本更高。

**D09 标准答案**  
工作区是当前事实真源，摘要是归纳结果。先事实后归纳可降低幻觉覆盖事实的风险。

**D10 标准答案**  
历史消息是弱信号，workspace/digest 是强结构化信号，这样可控且省 token。

**D11 标准答案**  
取 4 是折中值，够保语言连续又不显著膨胀；应通过 token 与回答稳定性压测调参。

**D12 标准答案**  
核心不是只靠提示词，而是节点白名单 + 执行时工具回调过滤双重约束。

**D13 标准答案**  
单靠提示词不够，必须配合状态机和节点出入口规则进行硬约束。

**D14 标准答案**  
即使模型跨节点发散，Workflow 仍按状态推进和工具白名单执行，不接受越权动作。

**D15 标准答案**  
报告节点应只消费证据，不再采证，避免“边写结论边改证据”破坏可审计性。

**D16 标准答案**  
可拆为：全局角色模板、节点任务模板、上下文渲染模板、输出契约模板四层。

**D17 标准答案**  
解决模型返回别名或格式差异导致“计划允许但执行找不到工具”的线上问题。

**D18 标准答案**  
可能误匹配相近工具名，需结合节点白名单和精确匹配优先策略降低风险。

**D19 标准答案**  
模型约束是软约束，工具入参校验是硬约束，必须双重防线。

**D20 标准答案**  
优点是流程不断裂、可降级；缺点是错误传播为文本，需统一错误码做后续治理。

**D21 标准答案**  
不会，只要原始结果仍落库并在消息元数据保留 raw 与 processed 映射。

**D22 标准答案**  
看链路：工具返回是否异常、压缩是否降质、最终解释是否偏离；分阶段定位最稳。

**D23 标准答案**  
去均值抑制直流分量，加窗降低频谱泄漏，FFT 才能得到更稳定的主峰与谐波信息。

**D24 标准答案**  
带通不合理会丢失冲击信息或引入噪声，应做参数范围校验并保守默认值。

**D25 标准答案**  
变速工况下仅看频率误差不稳，引入阶次容差可提升鲁棒性。

**D26 标准答案**  
应输出低置信度和不确定性说明，避免因样本稀少给出强结论。

**D27 标准答案**  
首次检索即触发索引可减少“未预建索引导致查不到”的冷启动问题。

**D28 标准答案**  
会有偏差风险，所以回退只做兜底，并应标注来源与置信度较低。

**D29 标准答案**  
标题语义更凝练、噪声更低；后续可做标题+正文融合向量提升召回深度。

**D30 标准答案**  
TopK=3 是“信息密度/噪声”折中；知识分散时调大，噪声高时调小并加强重排。

**D31 标准答案**  
检索 SQL 必须按 kbId 强过滤，彻底隔离不同知识库召回范围。

**D32 标准答案**  
应做版本化重建或增量重建，避免旧向量长期参与召回。

**D33 标准答案**  
瓶颈常在向量索引策略、维度规模、IO 和并发查询，需结合数据库侧观测优化。

**D34 标准答案**  
构建“问题-金标准片段”数据集，评估 TopK 命中率、首条命中率、误召回率。

**D35 标准答案**  
内存缓存保性能，数据库持久化保恢复能力，两者组合兼顾时延与可靠性。

**D36 标准答案**  
风险是内存态与持久态不一致；可通过写失败告警、重试和幂等写策略兜底。

**D37 标准答案**  
这些维度确保“同原文、同模型、同版本”才复用摘要，防止错命中。

**D38 标准答案**  
多租户或多场景建议纳入 key 维度，否则可能发生跨场景污染。

**D39 标准答案**  
当前 TTL 缓存是最终覆盖语义，高并发下应引入版本戳或 CAS 机制增强一致性。

**D40 标准答案**  
配置切换、模型切换、规则版本升级、已知错误数据修复时应主动失效。

**D41 标准答案**  
优先看模型思考耗时、工具执行耗时、总回合耗时，这三项最能定位瓶颈分层。

**D42 标准答案**  
对比 primary 与 compression 两阶段 token，结合 payload 压缩率判断净收益。

**D43 标准答案**  
可能是模型耗时高、工具计算重、数据库慢、或上下文处理链路成为瓶颈。

**D44 标准答案**  
说明尾延迟改善明显，但主流请求未显著变化，通常是长尾治理起效。

**D45 标准答案**  
固定数据集、固定并发、固定轮次、区分预热和实测、统一指标口径，才可复现。

**D46 标准答案**  
复用会话验证缓存和多轮连续性；不复用会话验证冷启动与基线能力。

**D47 标准答案**  
任意动态节点生成会破坏可控性和审计性，工业场景优先确定性边界。

**D48 标准答案**  
要改状态机转移规则、工作区快照版本管理、以及节点回滚幂等策略。

**D49 标准答案**  
阈值应外置配置并版本化，可按机型、工况、阶段进行策略分层。

**D50 标准答案**  
优先补齐：权限审计、告警和可观测、评测与回归、灰度发布与回滚、数据治理流程。

---

## 七、面试实战模板（建议直接填空）

### 1) 指标口径填空（必须准备真实数字）

- 平均回合耗时：优化前 `____ ms`，优化后 `____ ms`，下降 `____ %`
- P95 回合耗时：优化前 `____ ms`，优化后 `____ ms`，下降 `____ %`
- 缓存命中率：优化前 `____ %`，优化后 `____ %`
- 单轮 token：优化前 `____`，优化后 `____`，下降 `____ %`
- 召回命中率（TopK）：优化前 `____ %`，优化后 `____ %`
- 误召回率：优化前 `____ %`，优化后 `____ %`

### 2) 故障复盘模板（建议准备 2 例）

1. 故障现象：`____`
2. 触发条件：`____`
3. 影响范围：`____`
4. 根因定位：`____`
5. 修复方案：`____`
6. 防再发措施：`____`
7. 量化结果：`____`

### 3) 2 分钟源码级介绍模板

1. 入口与运行态：`JChatMindFactory.create()` 如何组装模型、工具、workflow、runtimeState。  
2. 编排执行：`JChatMind.run()` 如何循环 `nextPlan -> think -> execute`。  
3. 状态推进：`WindTurbineBearingWorkflow` 如何在工具结果后更新 workspace 并推进状态。  
4. 压缩与缓存：`DefaultToolResultProcessor`、`SessionRuntimeStateStore`、`ToolSummaryCache` 如何协同。  
5. 观测与压测：`AgentMetrics` 和 benchmark 脚本如何形成性能闭环。

### 4) 一周冲刺训练法（抗拷打）

- 第 1 天：背熟 B01-B28（每题 30 秒版本）。
- 第 2 天：背熟 E01-E24（每题 45 秒版本）。
- 第 3 天：背熟 D01-D50（每题 60 秒版本）。
- 第 4 天：做 2 次白板链路复述（不看稿）。
- 第 5 天：做 2 次高压追问模拟（同一题连续追 5 层）。
- 第 6 天：补齐真实指标数字和 2 个故障复盘。
- 第 7 天：全真面试演练 60 分钟并复盘薄弱点。
