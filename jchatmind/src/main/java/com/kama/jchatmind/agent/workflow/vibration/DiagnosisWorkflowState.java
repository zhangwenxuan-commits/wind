package com.kama.jchatmind.agent.workflow.vibration;

public enum DiagnosisWorkflowState {
    INIT,
    SELECT_DOCUMENT,
    LOAD_PARAMETER_CONTEXT,
    RUN_BASE_ANALYSIS,
    DECIDE_ADVANCED_ANALYSIS,
    RUN_ADVANCED_ANALYSIS,
    GENERATE_DIAGNOSIS,
    DONE
}
