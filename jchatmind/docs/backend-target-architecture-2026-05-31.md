# Backend Target Architecture Review

Date: 2026-05-31

## Judgment

The current backend is still a transitional structure:

- the product surface has shifted to diagnosis tasks
- but the codebase root naming still reflects the old chat-agent prototype
- and the `agent` module still mixes legacy chat runtime with some diagnosis workflow responsibilities

That is not the right steady state for Winds.

## Recommended naming

### Project and runtime names

- Maven artifact: `winds-diagnosis-backend`
- Spring application name: `winds-diagnosis-backend`
- Boot entrypoint: `WindsApplication`

### Package naming target

Do not stop at `com.kama.jchatmind`.

Preferred target package root:

- `com.kama.winds`

If a more domain-explicit root is preferred:

- `com.kama.winds.diagnosis`

## Recommended module split

The key is not “rename `agent`”, but “remove it from the primary business path”.

### 1. Primary diagnosis path

Owns the product core and should become the default backend architecture.

- `diagnosis.task`
- `diagnosis.analysis`
- `diagnosis.rule`
- `diagnosis.report`
- `asset.signal`
- `parameter`
- `knowledge`

### 2. Diagnosis orchestration path

Owns analysis sequencing, but should speak in diagnosis terms, not generic chat-agent terms.

- `diagnosis.orchestration`
- `diagnosis.orchestration.step`
- `diagnosis.orchestration.state`

This replaces the role currently half-played by `agent.workflow.vibration`.

### 3. Legacy compatibility path

Owns old chat/agent runtime only for compatibility.

- `legacy.agent`
- `legacy.chat`
- `legacy.runtime`
- `legacy.tool`

This makes it explicit that the old JChatMind runtime is no longer the product center.

## What should move out of the primary path

These are the strongest candidates to be demoted into `legacy.*`:

- `AgentController`
- `ChatSessionController`
- `ChatMessageController`
- `SseController`
- `ToolController`
- `agent/JChatMind.java`
- `agent/runtime/*`
- most generic tool abstractions that only serve chat turns

## What should replace them in the primary path

The main entry points should be diagnosis-native:

- `DiagnosisTaskController`
- `AnalysisRunController`
- `DiagnosisReportController`
- `SignalAssetController`
- `ParameterTemplateController`
- `DiagnosisRuleController`

And the main service seam should be something like:

- `DiagnosisTaskFacadeService`
- `DiagnosisAnalysisService`
- `DiagnosisRuleEngine`
- `DiagnosisReportService`

## Recommended migration order

1. Rename runtime/application identity first.
2. Freeze old chat APIs under `legacy` semantics.
3. Extract a diagnosis orchestration package from `agent.workflow.vibration`.
4. Migrate package root from `com.kama.jchatmind` to `com.kama.winds` in a dedicated sweep.
5. Remove or archive dead generic-agent abstractions after UI and API no longer depend on them.

## Important constraint

A full package rename should be done as a dedicated batch, not mixed into unrelated diagnosis feature work.

Reason:

- it touches imports, MyBatis XML namespaces, test packages, Jacoco includes, and Spring scan boundaries
- it creates review noise
- it hides real behavioral changes

So the correct approach is:

- first rename the runtime identity and document the architectural target
- then run one focused package migration batch
