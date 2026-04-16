package com.kama.jchatmind.agent.workflow.vibration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.runtime.RecentMessageSnapshot;
import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import com.kama.jchatmind.agent.runtime.ToolResponseProcessingResult;
import com.kama.jchatmind.agent.workflow.AgentWorkflow;
import com.kama.jchatmind.agent.workflow.WorkflowStepPlan;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class WindTurbineBearingWorkflow implements AgentWorkflow {

    private static final int MAX_NODE_STEPS = 8;

    private final ObjectMapper objectMapper;
    private final DiagnosisWorkspaceCache workspaceCache;
    private final AgentDTO.RuntimeCacheOptions runtimeCacheOptions;
    private final java.util.List<KnowledgeBaseDTO> availableKbs;
    private final Set<String> availableToolNames;
    private final boolean speedPathEnabled;
    private final SessionRuntimeState runtimeState;
    private final DiagnosisWorkspace workspace;

    private DiagnosisWorkflowState activeState;
    private boolean finished;

    public WindTurbineBearingWorkflow(
            ObjectMapper objectMapper,
            DiagnosisWorkspaceCache workspaceCache,
            AgentDTO.RuntimeCacheOptions runtimeCacheOptions,
            java.util.List<KnowledgeBaseDTO> availableKbs,
            Set<String> availableToolNames,
            boolean speedPathEnabled,
            SessionRuntimeState runtimeState,
            DiagnosisWorkspace workspace
    ) {
        this.objectMapper = objectMapper;
        this.workspaceCache = workspaceCache;
        this.runtimeCacheOptions = runtimeCacheOptions;
        this.availableKbs = availableKbs;
        this.availableToolNames = availableToolNames;
        this.speedPathEnabled = speedPathEnabled;
        this.runtimeState = runtimeState;
        this.workspace = workspace;
        if (!speedPathEnabled) {
            resetSpeedPathState();
        }
        this.activeState = workspace.getCurrentState();
        this.finished = workspace.getCurrentState() == DiagnosisWorkflowState.DONE;
    }

    @Override
    public WorkflowStepPlan nextPlan() {
        advanceStateIfPossible();
        if (workspace.getWorkflowStepCount() >= MAX_NODE_STEPS || workspace.getCurrentState() == DiagnosisWorkflowState.DONE) {
            workspace.setCurrentState(DiagnosisWorkflowState.DONE);
            persist();
            finished = true;
        }

        activeState = workspace.getCurrentState();
        workspace.setWorkflowStepCount(workspace.getWorkflowStepCount() + 1);
        persist();

        return switch (activeState) {
            case SELECT_DOCUMENT -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText("Step 1/5: select target MAT signals")
                    .systemPrompt(buildSelectDocumentPrompt())
                    .allowedToolNames(filterToolNames("listVibrationDocuments"))
                    .build();
            case LOAD_PARAMETER_CONTEXT -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText("Step 2/5: load bearing parameters and rules")
                    .systemPrompt(buildLoadParameterPrompt())
                    .allowedToolNames(filterToolNames("KnowledgeTool"))
                    .build();
            case RUN_BASE_ANALYSIS -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText("Step 3/5: run base spectrum analysis")
                    .systemPrompt(buildBaseAnalysisPrompt())
                    .allowedToolNames(filterToolNames("analyzeVibrationSpectrum"))
                    .build();
            case RUN_ADVANCED_ANALYSIS -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText(buildAdvancedAnalysisStatusText())
                    .systemPrompt(buildAdvancedAnalysisPrompt())
                    .allowedToolNames(buildAdvancedAnalysisToolNames())
                    .build();
            case GENERATE_DIAGNOSIS -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText("Step 5/5: generate diagnosis report")
                    .systemPrompt(buildReportPrompt())
                    .allowedToolNames(Set.of())
                    .build();
            case DONE -> WorkflowStepPlan.builder()
                    .state(activeState.name())
                    .statusText("Diagnosis finished")
                    .systemPrompt(buildReportPrompt())
                    .allowedToolNames(Set.of())
                    .build();
            case INIT, DECIDE_ADVANCED_ANALYSIS -> throw new IllegalStateException("Unexpected workflow state: " + activeState);
        };
    }

    @Override
    public void onAssistantResponse(AssistantMessage assistantMessage) {
        boolean hasToolCalls = assistantMessage != null
                && assistantMessage.getToolCalls() != null
                && !assistantMessage.getToolCalls().isEmpty();

        if (!hasToolCalls) {
            if (activeState == DiagnosisWorkflowState.GENERATE_DIAGNOSIS) {
                workspace.setCurrentState(DiagnosisWorkflowState.DONE);
                workspace.setAwaitingUserInput(false);
                finished = true;
            } else if (activeState == DiagnosisWorkflowState.RUN_ADVANCED_ANALYSIS) {
                workspace.addEvidence("Advanced analysis ended without additional tool execution. Falling back to report generation with current evidence.");
                workspace.setCurrentState(DiagnosisWorkflowState.GENERATE_DIAGNOSIS);
                workspace.setAwaitingUserInput(false);
                finished = false;
            } else {
                DiagnosisWorkflowState beforeAdvance = workspace.getCurrentState();
                advanceStateIfPossible();
                boolean progressed = workspace.getCurrentState() != beforeAdvance;
                if (progressed) {
                    workspace.setAwaitingUserInput(false);
                    finished = workspace.getCurrentState() == DiagnosisWorkflowState.DONE;
                } else {
                    workspace.setAwaitingUserInput(true);
                    finished = true;
                }
            }
            persist();
        }
    }

    @Override
    public void onToolResponses(ToolResponseProcessingResult processingResult) {
        if (processingResult == null || processingResult.getResponses() == null) {
            return;
        }

        for (ToolResponseProcessingResult.ProcessedToolResponse response : processingResult.getResponses()) {
            String toolName = response.getRawResponse().name();
            String payload = response.getRawResponse().responseData();
            switch (toolName) {
                case "listVibrationDocuments" -> handleDocumentList(payload);
                case "KnowledgeTool" -> handleKnowledgePayload(payload);
                case "analyzeVibrationSpectrum" -> handleSpectrumPayload(payload);
                case "analyzeEnvelopeSpectrum" -> handleEnvelopePayload(payload);
                case "analyzeSpeedSignal" -> handleSpeedPayload(payload);
                case "analyzeOrderSpectrum" -> handleOrderSpectrumPayload(payload);
                case "matchWindTurbineReferenceProfile" -> handleReferenceMatchPayload(payload);
                case "calculateBearingCharacteristicFrequencies" -> handleBearingFrequencyPayload(payload);
                default -> {
                    // ignore
                }
            }
        }

        workspace.setAwaitingUserInput(false);
        finished = false;
        advanceStateIfPossible();
        persist();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    private void handleDocumentList(String payload) {
        java.util.List<VibrationModels.DocumentSummary> documents = readValue(payload, new TypeReference<>() {
        });
        if (documents == null || documents.isEmpty()) {
            workspace.addEvidence("No ready MAT signal documents were found.");
            return;
        }
        workspace.setCandidateDocuments(documents);

        java.util.List<VibrationModels.DocumentSummary> vibrationCandidates = documents.stream()
                .filter(this::isVibrationCandidate)
                .toList();
        java.util.List<VibrationModels.DocumentSummary> speedCandidates = documents.stream()
                .filter(this::isSpeedCandidate)
                .toList();

        if (!StringUtils.hasText(workspace.getSelectedDocumentId())) {
            if (vibrationCandidates.size() == 1) {
                selectPrimaryDocument(vibrationCandidates.get(0));
            } else if (vibrationCandidates.isEmpty()
                    && documents.size() == 1
                    && !isSpeedCandidate(documents.get(0))) {
                selectPrimaryDocument(documents.get(0));
            } else if (vibrationCandidates.isEmpty() && !documents.isEmpty()) {
                workspace.addEvidence("Only speed or tachometer documents are available. A vibration MAT document is still required.");
            } else {
                workspace.addEvidence("Multiple candidate vibration documents were found. Waiting for user confirmation.");
            }
        }

        if (speedPathEnabled && !StringUtils.hasText(workspace.getSelectedSpeedDocumentId())) {
            if (speedCandidates.size() == 1) {
                selectSpeedDocument(speedCandidates.get(0));
            } else if (speedCandidates.size() > 1) {
                workspace.addEvidence("Multiple speed or tachometer documents were found. The workflow may need one user confirmation later.");
            }
        }
    }

    private void handleKnowledgePayload(String payload) {
        if (StringUtils.hasText(payload)) {
            workspace.setParameterContextLoaded(true);
            workspace.addEvidence("Loaded bearing parameters and diagnostic rules from knowledge.");
        }
    }

    private void handleSpectrumPayload(String payload) {
        VibrationModels.SpectrumAnalysis analysis = readValue(payload, new TypeReference<>() {
        });
        if (analysis == null) {
            return;
        }

        workspace.setSelectedDocumentId(analysis.getDocumentId());
        workspace.setSelectedDocumentName(analysis.getFilename());
        workspace.setBaseAnalysisCompleted(true);
        workspace.setCrestFactor(analysis.getBasicStats() != null ? analysis.getBasicStats().getCrestFactor() : null);
        workspace.setKurtosis(analysis.getBasicStats() != null ? analysis.getBasicStats().getKurtosis() : null);
        workspace.setHighFrequencyRatio(analysis.getHighFrequencyEnergyRatio());
        workspace.addEvidence("Base spectrum analysis completed.");
    }

    private void handleEnvelopePayload(String payload) {
        VibrationModels.EnvelopeSpectrumAnalysis analysis = readValue(payload, new TypeReference<>() {
        });
        if (analysis == null) {
            return;
        }
        workspace.setAdvancedAnalysisCompleted(true);
        workspace.addEvidence("Envelope spectrum analysis completed.");
    }

    private void handleSpeedPayload(String payload) {
        if (!speedPathEnabled) {
            return;
        }
        VibrationModels.SpeedSignalAnalysis analysis = readValue(payload, new TypeReference<>() {
        });
        if (analysis == null) {
            return;
        }
        workspace.setSelectedSpeedDocumentId(analysis.getDocumentId());
        workspace.setSelectedSpeedDocumentName(analysis.getFilename());
        workspace.setSpeedAnalysisCompleted(true);
        workspace.setReferenceRpm(analysis.getAverageRpm());
        workspace.addEvidence(String.format("Speed signal analyzed. Average speed %.2f rpm.", analysis.getAverageRpm()));
    }

    private void handleOrderSpectrumPayload(String payload) {
        if (!speedPathEnabled) {
            return;
        }
        VibrationModels.OrderSpectrumAnalysis analysis = readValue(payload, new TypeReference<>() {
        });
        if (analysis == null) {
            return;
        }
        workspace.setOrderSpectrumCompleted(true);
        workspace.setReferenceShaft(analysis.getReferenceShaft());
        workspace.setReferenceRpm(analysis.getReferenceRpm());
        workspace.addEvidence(String.format(
                "Order spectrum completed with reference shaft %s at %.2f rpm.",
                analysis.getReferenceShaft(),
                analysis.getReferenceRpm()
        ));
    }

    private void handleReferenceMatchPayload(String payload) {
        if (!speedPathEnabled) {
            return;
        }
        VibrationModels.WindTurbineReferenceMatchReport report = readValue(payload, new TypeReference<>() {
        });
        if (report == null) {
            return;
        }
        long matchedCount = report.getMatches() == null
                ? 0L
                : report.getMatches().stream().filter(VibrationModels.ReferenceFrequencyMatch::isMatched).count();
        workspace.setReferenceMatchEvaluated(true);
        workspace.setReferenceProfileMatched(matchedCount > 0);
        workspace.setReferenceShaft(report.getReferenceShaft());
        workspace.setReferenceRpm(report.getReferenceRpm());
        workspace.addEvidence(String.format(
                "Reference frequency matching completed. %d expected components matched.",
                matchedCount
        ));
    }

    private void handleBearingFrequencyPayload(String payload) {
        VibrationModels.BearingCharacteristicFrequencies analysis = readValue(payload, new TypeReference<>() {
        });
        if (analysis == null) {
            return;
        }
        workspace.setBearingFrequenciesCalculated(true);
        workspace.addEvidence("Bearing characteristic frequencies calculated.");
    }

    private void advanceStateIfPossible() {
        boolean advanced;
        do {
            advanced = false;
            DiagnosisWorkflowState currentState = workspace.getCurrentState();
            switch (currentState) {
                case INIT -> {
                    workspace.setCurrentState(DiagnosisWorkflowState.SELECT_DOCUMENT);
                    advanced = true;
                }
                case SELECT_DOCUMENT -> {
                    resolveUserDocumentSelection();
                    if (StringUtils.hasText(workspace.getSelectedDocumentId())) {
                        workspace.setCurrentState(DiagnosisWorkflowState.LOAD_PARAMETER_CONTEXT);
                        advanced = true;
                    }
                }
                case LOAD_PARAMETER_CONTEXT -> {
                    if (availableKbs.isEmpty() || Boolean.TRUE.equals(workspace.getParameterContextLoaded())) {
                        workspace.setCurrentState(DiagnosisWorkflowState.RUN_BASE_ANALYSIS);
                        advanced = true;
                    }
                }
                case RUN_BASE_ANALYSIS -> {
                    if (Boolean.TRUE.equals(workspace.getBaseAnalysisCompleted())) {
                        workspace.setCurrentState(DiagnosisWorkflowState.DECIDE_ADVANCED_ANALYSIS);
                        advanced = true;
                    }
                }
                case DECIDE_ADVANCED_ANALYSIS -> {
                    evaluateAdvancedNeed();
                    workspace.setCurrentState(Boolean.TRUE.equals(workspace.getAdvancedAnalysisRequired())
                            ? DiagnosisWorkflowState.RUN_ADVANCED_ANALYSIS
                            : DiagnosisWorkflowState.GENERATE_DIAGNOSIS);
                    advanced = true;
                }
                case RUN_ADVANCED_ANALYSIS -> {
                    if (canGenerateDiagnosisAfterAdvancedAnalysis()) {
                        workspace.setCurrentState(DiagnosisWorkflowState.GENERATE_DIAGNOSIS);
                        advanced = true;
                    }
                }
                default -> {
                }
            }
        } while (advanced);
    }

    private void evaluateAdvancedNeed() {
        double crest = workspace.getCrestFactor() != null ? workspace.getCrestFactor() : 0.0;
        double kurtosis = workspace.getKurtosis() != null ? workspace.getKurtosis() : 0.0;
        double highFrequencyRatio = workspace.getHighFrequencyRatio() != null ? workspace.getHighFrequencyRatio() : 0.0;
        boolean required = crest >= 4.5 || kurtosis >= 4.5 || highFrequencyRatio >= 0.45;
        workspace.setAdvancedAnalysisRequired(required);
        workspace.setLastDecisionReason(required
                ? "Base spectrum indicates impact or high-frequency abnormality, move to advanced analysis."
                : "Base spectrum evidence is sufficient, generate diagnosis directly.");
        workspace.addEvidence(workspace.getLastDecisionReason());
    }

    private String buildSelectDocumentPrompt() {
        if (!speedPathEnabled) {
            return """
                    You are in the SELECT_DOCUMENT node of a wind-turbine bearing diagnosis workflow.

                    Goal:
                    - Identify the primary vibration MAT document to analyze.

                    Rules:
                    1. If no document has been selected yet, call listVibrationDocuments(kbId) first.
                    2. Use signal name, unit, and device name to distinguish vibration signals from non-vibration files.
                    3. If there is only one clear vibration candidate, do not ask the user for confirmation.
                    4. If there are multiple vibration candidates, ask the user one concise clarifying question.
                    5. If no document is available, clearly tell the user that a MAT vibration file is required.
                    6. Do not diagnose faults at this node.
                    """;
        }
        return """
                You are in the SELECT_DOCUMENT node of a wind-turbine bearing diagnosis workflow.

                Goal:
                - Identify the primary vibration MAT document to analyze.
                - If a speed or tachometer MAT document is uniquely available, remember it for later order analysis.

                Rules:
                1. If no document has been selected yet, call listVibrationDocuments(kbId) first.
                2. Use signal name, unit, and device name to distinguish vibration signals from speed or tachometer signals.
                3. If there is only one clear vibration candidate, do not ask the user for confirmation.
                4. If there are multiple vibration candidates, ask the user one concise clarifying question.
                5. If no document is available, clearly tell the user that a MAT vibration file is required.
                6. Do not diagnose faults at this node.
                """;
    }

    private String buildLoadParameterPrompt() {
        return """
                You are in the LOAD_PARAMETER_CONTEXT node of a wind-turbine bearing diagnosis workflow.

                Goal:
                - Load signal-to-component mapping, relative fault-frequency references, shaft speed context, envelope band hints, and diagnosis rules.

                Rules:
                1. Use KnowledgeTool to retrieve bearing parameters and diagnosis rules.
                2. Prioritize sensor channel mapping, recommended envelope band, preferred reference shaft, and relative frequency tables for the selected channel or component.
                3. Bearing geometry parameters are optional in this project stage. If relative frequency tables or the built-in wind-turbine reference profile are available, continue without asking the user for rolling element count, diameter, pitch diameter, or contact angle.
                4. Ask the user one concise follow-up question only when both relative frequency references and geometry data are unavailable, or when the shaft/component mapping is still ambiguous.
                5. Do not produce final diagnosis at this node.
                """;
    }

    private String buildBaseAnalysisPrompt() {
        return """
                You are in the RUN_BASE_ANALYSIS node of a wind-turbine bearing diagnosis workflow.

                Goal:
                - Run base spectrum analysis for the selected MAT document.

                Rules:
                1. If selectedDocumentId is available, call analyzeVibrationSpectrum(documentId).
                2. Focus on dominant peaks, harmonics, crest factor, kurtosis, and high-frequency energy ratio.
                3. Do not generate the final diagnosis at this node.
                """;
    }

    private String buildAdvancedAnalysisPrompt() {
        if (!speedPathEnabled) {
            return """
                    You are in the RUN_ADVANCED_ANALYSIS node of a wind-turbine bearing diagnosis workflow.

                    Goal:
                    - Run advanced evidence collection with envelope analysis and supplemental bearing-frequency checks.

                    Rules:
                    1. Prefer analyzeEnvelopeSpectrum(documentId, bandHint) when impact features are present.
                    2. Call calculateBearingCharacteristicFrequencies(...) only when explicit geometry parameters are available; it is supplemental, not mandatory.
                    3. If parameters are still missing, you may call KnowledgeTool one more time, but do not loop indefinitely.
                    4. Do not call analyzeSpeedSignal, analyzeOrderSpectrum, or matchWindTurbineReferenceProfile in this run.
                    5. Once envelope or other non-speed evidence is sufficient, move on so the next node can generate the diagnosis report.
                    6. Keep this node focused on evidence collection, not on the final report.
                    """;
        }
        return """
                You are in the RUN_ADVANCED_ANALYSIS node of a wind-turbine bearing diagnosis workflow.

                Goal:
                - Run advanced evidence collection with envelope analysis, speed analysis, order analysis, and reference-frequency matching when available.

                Rules:
                1. If impact features are present, prefer analyzeEnvelopeSpectrum(documentId, bandHint).
                2. If a speed document is not yet known and order analysis is valuable, call listVibrationDocuments(kbId) to find a speed or tachometer MAT signal.
                3. If selectedSpeedDocumentId is available and speedAnalysisCompleted is false, you must call analyzeSpeedSignal(speedDocumentId) before ending this node.
                4. If selectedSpeedDocumentId is available and speedAnalysisCompleted is true, prefer analyzeOrderSpectrum(vibrationDocumentId, speedDocumentId, referenceShaft) or matchWindTurbineReferenceProfile(...) when the remaining parameters are clear enough.
                5. Prefer matchWindTurbineReferenceProfile(...) with the built-in wind-turbine parameter card and the retrieved relative frequency tables, even when bearing geometry is unavailable.
                6. Call calculateBearingCharacteristicFrequencies(...) only when explicit geometry parameters are available; it is supplemental, not mandatory for first-stage diagnosis.
                7. If parameters are still missing, you may call KnowledgeTool one more time, but do not loop indefinitely.
                8. Do not stop after envelope analysis alone when a speed document is available and speed analysis has not been completed yet.
                9. If speed analysis is already completed and no stronger follow-up tool call is necessary, you may move on so the next node can generate a first-stage diagnosis report with an explicit uncertainty statement.
                10. Keep this node focused on evidence collection, not on the final report.
                """;
    }

    private boolean canGenerateDiagnosisAfterAdvancedAnalysis() {
        boolean hasSpeedDocument = speedPathEnabled && StringUtils.hasText(workspace.getSelectedSpeedDocumentId());
        if (hasSpeedDocument && !Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted())) {
            return false;
        }

        return Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted())
                || Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted())
                || Boolean.TRUE.equals(workspace.getBearingFrequenciesCalculated())
                || Boolean.TRUE.equals(workspace.getOrderSpectrumCompleted())
                || Boolean.TRUE.equals(workspace.getReferenceMatchEvaluated());
    }

    private String buildAdvancedAnalysisStatusText() {
        return speedPathEnabled
                ? "Step 4/5: run advanced analysis with envelope, speed, and reference matching"
                : "Step 4/5: run advanced analysis with envelope and supplemental checks";
    }

    private Set<String> buildAdvancedAnalysisToolNames() {
        if (!speedPathEnabled) {
            return filterToolNames(
                    "listVibrationDocuments",
                    "analyzeEnvelopeSpectrum",
                    "calculateBearingCharacteristicFrequencies",
                    "KnowledgeTool"
            );
        }
        return filterToolNames(
                "listVibrationDocuments",
                "analyzeEnvelopeSpectrum",
                "analyzeSpeedSignal",
                "analyzeOrderSpectrum",
                "matchWindTurbineReferenceProfile",
                "calculateBearingCharacteristicFrequencies",
                "KnowledgeTool"
        );
    }

    private String buildReportPrompt() {
        return """
                You are in the GENERATE_DIAGNOSIS node of a wind-turbine bearing diagnosis workflow.

                Output requirements:
                - diagnosis conclusion
                - key evidence
                - risk level
                - recommended action
                - uncertainty statement

                Rules:
                1. Answer strictly based on the current workspace, conversation digest, and tool results.
                2. Do not invent unobserved fault frequencies or unsupported conclusions.
                3. If evidence is insufficient, explicitly state what is missing.
                4. Do not call tools at this node.
                """;
    }

    private Set<String> filterToolNames(String... names) {
        Set<String> expected = Set.of(names);
        return availableToolNames.stream()
                .filter(expected::contains)
                .collect(Collectors.toSet());
    }

    private void resolveUserDocumentSelection() {
        if (StringUtils.hasText(workspace.getSelectedDocumentId())) {
            return;
        }
        if (workspace.getCandidateDocuments() == null || workspace.getCandidateDocuments().isEmpty()) {
            return;
        }

        for (String userReply : recentUserReplies()) {
            VibrationModels.DocumentSummary selected = selectCandidateFromUserReply(userReply);
            if (selected == null) {
                continue;
            }
            selectPrimaryDocument(selected);
            workspace.addEvidence("Selected vibration document from user confirmation: " + selected.getFilename());
            return;
        }
    }

    private List<String> recentUserReplies() {
        List<String> replies = new java.util.ArrayList<>();
        if (runtimeState == null || runtimeState.getRecentMessages() == null || runtimeState.getRecentMessages().isEmpty()) {
            return replies;
        }
        List<RecentMessageSnapshot> recentMessages = runtimeState.getRecentMessages();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            RecentMessageSnapshot snapshot = recentMessages.get(i);
            if (snapshot != null
                    && snapshot.getRole() == com.kama.jchatmind.model.dto.ChatMessageDTO.RoleType.USER
                    && StringUtils.hasText(snapshot.getContent())) {
                replies.add(snapshot.getContent());
            }
        }
        return replies;
    }

    private VibrationModels.DocumentSummary selectCandidateFromUserReply(String userReply) {
        if (!StringUtils.hasText(userReply)) {
            return null;
        }

        List<VibrationModels.DocumentSummary> vibrationCandidates = workspace.getCandidateDocuments().stream()
                .filter(this::isVibrationCandidate)
                .toList();
        if (vibrationCandidates.isEmpty()) {
            return null;
        }

        String normalizedReply = normalizeReply(userReply);
        VibrationModels.DocumentSummary indexedChoice = selectByIndexHint(normalizedReply, vibrationCandidates);
        if (indexedChoice != null) {
            return indexedChoice;
        }

        List<VibrationModels.DocumentSummary> matched = vibrationCandidates.stream()
                .filter(candidate -> matchesCandidate(normalizedReply, candidate))
                .toList();

        if (matched.size() == 1) {
            return matched.get(0);
        }
        return null;
    }

    private VibrationModels.DocumentSummary selectByIndexHint(
            String normalizedReply,
            List<VibrationModels.DocumentSummary> vibrationCandidates
    ) {
        if (normalizedReply == null) {
            return null;
        }
        if (normalizedReply.equals("1")
                || normalizedReply.contains("第一个")
                || normalizedReply.contains("第1个")
                || normalizedReply.contains("第一个文档")) {
            return vibrationCandidates.get(0);
        }
        if (vibrationCandidates.size() > 1
                && (normalizedReply.equals("2")
                || normalizedReply.contains("第二个")
                || normalizedReply.contains("第2个")
                || normalizedReply.contains("第二个文档"))) {
            return vibrationCandidates.get(1);
        }
        return null;
    }

    private boolean matchesCandidate(String normalizedReply, VibrationModels.DocumentSummary candidate) {
        if (candidate == null || normalizedReply == null) {
            return false;
        }
        String filename = normalizeReply(candidate.getFilename());
        String documentId = normalizeReply(candidate.getDocumentId());
        String signalName = normalizeReply(candidate.getSignalName());
        String deviceName = normalizeReply(candidate.getDeviceName());
        String stem = filename;
        int dotIndex = stem.lastIndexOf('.');
        if (dotIndex > 0) {
            stem = stem.substring(0, dotIndex);
        }

        return normalizedReply.contains(filename)
                || normalizedReply.contains(stem)
                || normalizedReply.contains(documentId)
                || (StringUtils.hasText(signalName) && normalizedReply.contains(signalName))
                || (StringUtils.hasText(deviceName) && normalizedReply.contains(deviceName));
    }

    private String normalizeReply(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isVibrationCandidate(VibrationModels.DocumentSummary document) {
        if (document == null) {
            return false;
        }
        return Boolean.TRUE.equals(document.getHasVibrationSignal())
                || "VIBRATION".equalsIgnoreCase(document.getSignalRole())
                || "MIXED".equalsIgnoreCase(document.getSignalRole())
                || !isSpeedCandidate(document);
    }

    private boolean isSpeedCandidate(VibrationModels.DocumentSummary document) {
        return document != null
                && (Boolean.TRUE.equals(document.getHasSpeedSignal())
                || "SPEED".equalsIgnoreCase(document.getSignalRole())
                || "MIXED".equalsIgnoreCase(document.getSignalRole()));
    }

    private void selectPrimaryDocument(VibrationModels.DocumentSummary document) {
        workspace.setSelectedDocumentId(document.getDocumentId());
        workspace.setSelectedDocumentName(document.getFilename());
        workspace.setSelectedKbId(document.getKbId());
        workspace.addEvidence("Auto-selected the primary vibration document: " + document.getFilename());
        if (speedPathEnabled
                && !StringUtils.hasText(workspace.getSelectedSpeedDocumentId())
                && Boolean.TRUE.equals(document.getHasSpeedSignal())) {
            selectSpeedDocument(document);
        }
    }

    private void selectSpeedDocument(VibrationModels.DocumentSummary document) {
        workspace.setSelectedSpeedDocumentId(document.getDocumentId());
        workspace.setSelectedSpeedDocumentName(document.getFilename());
        if (!StringUtils.hasText(workspace.getReferenceShaft()) && StringUtils.hasText(document.getReferenceShaftHint())) {
            workspace.setReferenceShaft(document.getReferenceShaftHint());
        }
        workspace.addEvidence("Auto-selected the speed document for later order analysis: " + document.getFilename());
    }

    private <T> T readValue(String payload, TypeReference<T> typeReference) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, typeReference);
        } catch (Exception e) {
            workspace.addEvidence("Failed to parse tool output. Falling back to raw text for later model reasoning.");
            return null;
        }
    }

    private void persist() {
        workspaceCache.put(
                workspace.getSessionId(),
                runtimeCacheOptions.getSessionMemoryTtlSeconds(),
                workspace
        );
    }

    private void resetSpeedPathState() {
        workspace.setSelectedSpeedDocumentId(null);
        workspace.setSelectedSpeedDocumentName(null);
        workspace.setReferenceShaft(null);
        workspace.setReferenceRpm(null);
        workspace.setSpeedAnalysisCompleted(false);
        workspace.setOrderSpectrumCompleted(false);
        workspace.setReferenceMatchEvaluated(false);
        workspace.setReferenceProfileMatched(false);
    }
}
