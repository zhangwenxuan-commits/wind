CREATE TABLE IF NOT EXISTS diagnosis_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    rule_code TEXT NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    rule_type TEXT NOT NULL,
    applicability TEXT,
    component_scope TEXT,
    signal_domain TEXT,
    metric_key TEXT,
    comparator TEXT,
    threshold_warn DOUBLE PRECISION,
    threshold_alert DOUBLE PRECISION,
    frequency_band_hint TEXT,
    pattern_text TEXT NOT NULL,
    recommendation TEXT,
    source_title TEXT NOT NULL,
    source_url TEXT NOT NULL,
    source_published_at TEXT,
    provenance TEXT NOT NULL,
    import_batch TEXT,
    notes TEXT,
    metadata JSONB,

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),

    CONSTRAINT uq_diagnosis_rule_code UNIQUE (rule_code)
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_rule_type_status
ON diagnosis_rule(rule_type, status);

CREATE INDEX IF NOT EXISTS idx_diagnosis_rule_metric_key
ON diagnosis_rule(metric_key);
