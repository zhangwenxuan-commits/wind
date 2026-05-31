# Diagnosis Rule Architecture Review

Date: 2026-05-31  
Scope: `jchatmind` backend diagnosis flow

## What changed

1. Added a dedicated `diagnosis_rule` catalog table instead of keeping all rule knowledge in hard-coded Java branches or unstructured KB text.
2. Added `DiagnosisRuleProfileResolver` to merge three sources in a deterministic order:
   - active database threshold rules
   - `parameter_template.content.thresholds`
   - built-in fallback defaults
3. Added rule-audit output into runtime artifacts:
   - `diagnosis_task.metadata.latestAnalysis.appliedRules`
   - `analysis_run.metadata.appliedRules`
   - diagnosis report markdown section `规则依据`
4. Extracted report markdown generation into `DiagnosisReportBuilder`, so `DiagnosisTaskFacadeServiceImpl` no longer owns report formatting.

## Why this is better

- The previous flow mixed business rules with orchestration code in `DiagnosisTaskAnalyzer` and `VibrationAnalysisServiceImpl`.
- Threshold provenance was invisible after a run. Reviewers could see a risk level, but not the rule source behind it.
- The new split makes rule data queryable, seedable, and reviewable without changing Java every time.

## Current runtime boundary

- `THRESHOLD` rules are executable today.
- `PATTERN` rules are cataloged and queryable now, but they are not yet auto-evaluated by the task engine.
- This was deliberate: catalog first, then add a pattern-evaluation engine once the signal-matching contract is stable.

## Remaining gaps

1. No admin CRUD yet for `diagnosis_rule`; current ingestion is SQL-seeded.
2. Pattern rules are stored, but harmonic/BPFO/BPFI matching still lives in heuristic service code.
3. Rule applicability is not yet scoped by turbine model, bearing position, or operating condition.
4. There is no explicit rule-version foreign key on `analysis_run`; current traceability relies on embedded rule snapshots.

## Recommended next step

1. Add `/api/diagnosis-rules` management for create/update/disable.
2. Bind rule subsets by `device_model`, `bearing_position`, and `operating_band`.
3. Promote BPFO/BPFI/BSF/FTF pattern checks into a dedicated rule-evaluation stage.
4. Persist a `rule_snapshot_hash` or `rule_batch_id` on `analysis_run` for stronger audit semantics.
