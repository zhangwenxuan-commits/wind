package com.kama.jchatmind.agent.workflow.vibration;

import com.kama.jchatmind.service.vibration.VibrationModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagnosisWorkspace {

    private String taskType;
    private String sessionId;
    private String userGoal;
    private DiagnosisWorkflowState currentState;
    private String selectedDocumentId;
    private String selectedDocumentName;
    private String selectedSpeedDocumentId;
    private String selectedSpeedDocumentName;
    private String selectedKbId;
    private String referenceShaft;
    private Double referenceRpm;
    private Boolean parameterContextLoaded;
    private Boolean baseAnalysisCompleted;
    private Boolean advancedAnalysisRequired;
    private Boolean advancedAnalysisCompleted;
    private Boolean speedAnalysisCompleted;
    private Boolean orderSpectrumCompleted;
    private Boolean referenceMatchEvaluated;
    private Boolean referenceProfileMatched;
    private Boolean bearingFrequenciesCalculated;
    private Boolean awaitingUserInput;
    private Double crestFactor;
    private Double kurtosis;
    private Double highFrequencyRatio;
    private String lastDecisionReason;
    private List<String> evidenceNotes;
    private List<VibrationModels.DocumentSummary> candidateDocuments;
    private int workflowStepCount;

    public static DiagnosisWorkspace create(String sessionId, String userGoal, String defaultKbId) {
        return DiagnosisWorkspace.builder()
                .taskType("wind-turbine-bearing-diagnosis")
                .sessionId(sessionId)
                .userGoal(userGoal)
                .currentState(DiagnosisWorkflowState.INIT)
                .selectedKbId(defaultKbId)
                .parameterContextLoaded(false)
                .baseAnalysisCompleted(false)
                .advancedAnalysisCompleted(false)
                .speedAnalysisCompleted(false)
                .orderSpectrumCompleted(false)
                .referenceMatchEvaluated(false)
                .referenceProfileMatched(false)
                .bearingFrequenciesCalculated(false)
                .awaitingUserInput(false)
                .evidenceNotes(new ArrayList<>())
                .candidateDocuments(new ArrayList<>())
                .workflowStepCount(0)
                .build();
    }

    public void addEvidence(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return;
        }
        if (evidenceNotes == null) {
            evidenceNotes = new ArrayList<>();
        }
        evidenceNotes.add(evidence);
    }
}
