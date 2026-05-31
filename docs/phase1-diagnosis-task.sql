CREATE TABLE IF NOT EXISTS diagnosis_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    title TEXT NOT NULL,
    device_name TEXT,
    status TEXT NOT NULL,
    risk_level TEXT,

    vibration_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
    speed_document_id UUID REFERENCES document(id) ON DELETE SET NULL,
    parameter_kb_id UUID REFERENCES knowledge_base(id) ON DELETE SET NULL,

    summary TEXT,
    metadata JSONB,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_task_status
ON diagnosis_task(status);
