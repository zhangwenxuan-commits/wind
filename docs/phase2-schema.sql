CREATE TABLE IF NOT EXISTS parameter_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name TEXT NOT NULL,
    device_model TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    reference_shaft TEXT,
    envelope_band_hint TEXT,
    content JSONB,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

ALTER TABLE diagnosis_task
ADD COLUMN IF NOT EXISTS parameter_template_id UUID REFERENCES parameter_template(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS analysis_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    task_id UUID NOT NULL REFERENCES diagnosis_task(id) ON DELETE CASCADE,
    run_no INTEGER NOT NULL,
    status TEXT NOT NULL,
    risk_level TEXT,
    summary TEXT,
    metadata JSONB,

    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS analysis_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    run_id UUID NOT NULL REFERENCES analysis_run(id) ON DELETE CASCADE,
    evidence_type TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT,
    score DOUBLE PRECISION,
    metadata JSONB,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS diagnosis_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    task_id UUID NOT NULL REFERENCES diagnosis_task(id) ON DELETE CASCADE,
    run_id UUID REFERENCES analysis_run(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    title TEXT NOT NULL,
    summary TEXT,
    content_markdown TEXT NOT NULL,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_analysis_run_task_id
ON analysis_run(task_id);

CREATE INDEX IF NOT EXISTS idx_analysis_evidence_run_id
ON analysis_evidence(run_id);

CREATE INDEX IF NOT EXISTS idx_diagnosis_report_task_id
ON diagnosis_report(task_id);
