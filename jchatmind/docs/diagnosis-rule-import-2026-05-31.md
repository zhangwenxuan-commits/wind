# Diagnosis Rule Import Record

Date: 2026-05-31  
Batch: `2026-05-31-web-rule-seed-v1`

## Purpose

Seed a first structured rule catalog for wind-turbine bearing diagnosis, with source traceability preserved in SQL and database rows.

## Imported shape

- `THRESHOLD` rules: 1
- `PATTERN` rules: 10
- Primary runtime metric covered by external threshold in this batch: `KURTOSIS`

## Sources used

1. MDPI Energies: *Test Investigation and Rule Analysis of Bearing Fault Diagnosis in Induction Motors*  
   https://www.mdpi.com/1996-1073/16/2/699
2. Fluke: *What Is The “Crest Factor” And Why Is It Used?*  
   https://www.fluke.com/en-us/learn/blog/vibration/what-is-the-crest-factor-and-why-is-it-used
3. Dewesoft: *How to Interpret Condition Monitoring Data*  
   https://dewesoft.com/blog/how-to-interpret-condition-monitoring-data
4. Dewesoft: *Bearing envelope analysis*  
   https://dewesoft.com/applications/bearing-envelope-analysis
5. Fluke: *Mechanical looseness: what it is and how to detect it*  
   https://www.fluke.com/ja-jp/learn/blog/predictive-maintenance/mechanical-looseness-what-it-is-how-to-detect

## Files

- Migration: [phase2-diagnosis-rule.sql](/home/wenxuan/IdeaProjects/winds/jchatmind/docs/phase2-diagnosis-rule.sql)
- Seed batch: [seed-diagnosis-rules-2026-05-31.sql](/home/wenxuan/IdeaProjects/winds/jchatmind/docs/seed-diagnosis-rules-2026-05-31.sql)

## Notes

- Only the kurtosis warning threshold in this batch is source-backed as a direct numeric threshold.
- The other imported items are pattern rules or operator hints. They are stored now for review and later automation.
- When a source described a frequency zone rather than a scalar alarm value, the rule was stored as `PATTERN` plus `frequency_band_hint`.
- This batch intentionally leaves final alert thresholds for some metrics in application defaults until stronger primary sources are curated.

## Execution Record

- Execution target: local PostgreSQL `jchatmind`
- Execution method: JDBC statement execution using the project runtime PostgreSQL driver
- Execution date: 2026-05-31
- Verification result:
  - total imported in batch: `11`
  - threshold rules: `1`
  - pattern rules: `10`
