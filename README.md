# Winds

Winds 是一个面向风机轴承振动分析与故障诊断的智能诊断工作台。

这次重构的目标是把项目从“通用智能体壳”切换为“任务驱动的诊断产品”：

- 用户入口从聊天切到诊断任务
- 数据、参数、分析结果和结论以结构化页面呈现
- AI 保留为分析与解释能力，不再作为主产品概念暴露

## 当前阶段

本仓库已完成 Phase 1 的核心重构：

- 新的主导航：`工作台 / 诊断任务 / 数据资产 / 参数与知识 / 报告中心 / 系统配置`
- 新的后端任务接口：`/api/diagnosis-tasks`
- 新的数据资产接口：`/api/signal-assets`
- 新的参数源接口：`/api/parameter-sources`
- 诊断任务详情页支持直接触发分析，并把结果沉淀为结构化任务结果

当前仍保留旧聊天页和旧知识库页作为兼容入口：

- `/legacy/chat`
- `/legacy/knowledge-base`

## 目录

```text
winds/
  docs/                    需求文档、信息架构、后端重构方案、交付表
  ui/                      React + Vite 前端
  jchatmind/               Spring Boot + MyBatis 后端
  jchatmind.sql            数据库初始化脚本
```

## 关键文档

- [重构需求文档](docs/winds-refactor-prd.md)
- [信息架构与页面设计草案](docs/winds-information-architecture.md)
- [后端重构方案草案](docs/winds-backend-refactor-plan.md)
- [Phase 1 交付表](docs/phase1-delivery.md)

## 技术栈

### 前端

- React 19
- React Router 7
- Ant Design 6
- Vite 5
- TypeScript

### 后端

- Spring Boot 3
- MyBatis
- PostgreSQL
- Spring AI

## 已实现的核心流程

1. 在 `数据资产` 上传 MAT 文件
2. 在 `诊断任务` 新建任务并绑定振动/转速文件
3. 选择参数源
4. 在任务详情页执行分析
5. 查看结构化分析结果、证据摘要和结论
6. 人工确认诊断结论

## 数据库

这次重构新增了 `diagnosis_task` 表。

如果你的本地数据库已经初始化过旧版本，需要把 [jchatmind.sql](jchatmind.sql) 中 `diagnosis_task` 的 DDL 同步到现有库。

也可以直接执行最小迁移脚本：

- [docs/phase1-diagnosis-task.sql](docs/phase1-diagnosis-task.sql)

## 本地运行

### 1. 后端

```bash
cd jchatmind
mvn spring-boot:run
```

默认配置：

- 端口：`8080`
- 数据库：`jdbc:postgresql://localhost:5432/jchatmind`

### 2. 前端

```bash
cd ui
npm install
npm run dev
```

默认访问地址：

- `http://localhost:5173`

## 测试

### 后端单元测试

```bash
cd jchatmind
mvn verify
```

本轮重构核心后端模块已接入 JaCoCo 覆盖率检查，覆盖范围包括：

- `DiagnosisTaskAnalyzer`
- `DiagnosisTaskFacadeServiceImpl`
- `SignalAssetFacadeServiceImpl`
- `ParameterSourceFacadeServiceImpl`
- `DiagnosisTaskConverter`

覆盖率报告输出位置：

- `jchatmind/target/site/jacoco/index.html`

### 前端构建校验

```bash
cd ui
npm run build
```

## 当前限制

1. `参数与知识` 目前仍复用原有知识库作为参数源，尚未完全结构化
2. `报告中心` 和 `系统配置` 仍是 Phase 1 占位模块
3. 任务分析结果当前复用现有振动分析服务，后续还需要进一步拆出独立的任务编排和报告模型
4. 数据资产底层暂时仍依赖现有 `document` 存储链路，后续可以再演进为独立 `signal_asset` 模型

## 下一步建议

1. 把参数模板从 Markdown/知识库逐步迁移到结构化表模型
2. 为任务分析增加 `analysis_run / analysis_evidence / diagnosis_report`
3. 把任务详情页的结果面板继续增强为频谱、包络、阶次等图形视图
4. 为报告中心补齐正式导出与修订历史
