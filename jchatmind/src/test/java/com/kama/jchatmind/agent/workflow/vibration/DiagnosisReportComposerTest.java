package com.kama.jchatmind.agent.workflow.vibration;

import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisReportComposerTest {

    @Test
    void shouldRenderStructuredFallbackReportFromWorkspace() {
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-1", "agent-1");
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-1", "diagnose bearing", "kb-1");
        workspace.setSelectedDocumentName("bearing_vibration.mat");
        workspace.setBaseAnalysisCompleted(true);
        workspace.setAdvancedAnalysisCompleted(true);
        workspace.setCrestFactor(5.1);
        workspace.setKurtosis(4.9);
        workspace.setHighFrequencyRatio(0.52);
        workspace.addEvidence("Envelope spectrum analysis completed.");
        runtimeState.setDiagnosisWorkspace(workspace);

        String report = DiagnosisReportComposer.compose(runtimeState);

        assertTrue(report.contains("诊断结论"));
        assertTrue(report.contains("关键证据"));
        assertTrue(report.contains("风险等级"));
        assertTrue(report.contains("建议动作"));
        assertTrue(report.contains("不确定性说明"));
        assertTrue(report.contains("bearing_vibration.mat"));
    }
}
