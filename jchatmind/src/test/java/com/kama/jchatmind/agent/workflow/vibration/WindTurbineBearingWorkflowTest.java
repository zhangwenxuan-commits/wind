package com.kama.jchatmind.agent.workflow.vibration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.runtime.RecentMessageSnapshot;
import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import com.kama.jchatmind.agent.runtime.ToolResponseProcessingResult;
import com.kama.jchatmind.agent.workflow.WorkflowStepPlan;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.service.vibration.VibrationModels;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindTurbineBearingWorkflowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReuseSameDocumentWhenMatContainsVibrationAndSpeedChannels() throws Exception {
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-mixed", "analyze mixed mat", "kb-1");
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-mixed", "agent-1");
        WindTurbineBearingWorkflow workflow = new WindTurbineBearingWorkflow(
                objectMapper,
                new DiagnosisWorkspaceCache(new AgentMetrics(new SimpleMeterRegistry())),
                AgentDTO.RuntimeCacheOptions.defaultOptions(),
                List.of(KnowledgeBaseDTO.builder().id("kb-1").name("wind-turbine-knowledge").build()),
                Set.of("listVibrationDocuments"),
                true,
                runtimeState,
                workspace
        );

        WorkflowStepPlan plan = workflow.nextPlan();
        assertEquals("SELECT_DOCUMENT", plan.state());

        workflow.onToolResponses(response(
                "listVibrationDocuments",
                objectMapper.writeValueAsString(List.of(
                        VibrationModels.DocumentSummary.builder()
                                .documentId("mixed-1")
                                .kbId("kb-1")
                                .filename("drivetrain_signals.mat")
                                .signalRole("MIXED")
                                .hasVibrationSignal(true)
                                .hasSpeedSignal(true)
                                .defaultSpeedSignalName("Speed")
                                .referenceShaftHint("HSS")
                                .build()
                ))
        ));

        assertEquals("mixed-1", workspace.getSelectedDocumentId());
        assertEquals("mixed-1", workspace.getSelectedSpeedDocumentId());
        assertEquals("HSS", workspace.getReferenceShaft());
    }

    @Test
    void shouldGenerateDiagnosisAfterSpeedAnalysisCompletes() throws Exception {
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-1", "analyze wind turbine bearing", "kb-1");
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-1", "agent-1");
        WindTurbineBearingWorkflow workflow = new WindTurbineBearingWorkflow(
                objectMapper,
                new DiagnosisWorkspaceCache(new AgentMetrics(new SimpleMeterRegistry())),
                AgentDTO.RuntimeCacheOptions.defaultOptions(),
                List.of(KnowledgeBaseDTO.builder().id("kb-1").name("wind-turbine-knowledge").build()),
                Set.of(
                        "listVibrationDocuments",
                        "KnowledgeTool",
                        "analyzeVibrationSpectrum",
                        "analyzeEnvelopeSpectrum",
                        "analyzeSpeedSignal",
                        "analyzeOrderSpectrum",
                        "matchWindTurbineReferenceProfile",
                        "calculateBearingCharacteristicFrequencies"
                ),
                true,
                runtimeState,
                workspace
        );

        WorkflowStepPlan plan1 = workflow.nextPlan();
        assertEquals("SELECT_DOCUMENT", plan1.state());
        assertTrue(plan1.allowedToolNames().contains("listVibrationDocuments"));

        workflow.onToolResponses(response(
                "listVibrationDocuments",
                objectMapper.writeValueAsString(List.of(
                        VibrationModels.DocumentSummary.builder()
                                .documentId("vib-1")
                                .kbId("kb-1")
                                .filename("bearing_vibration.mat")
                                .signalRole("VIBRATION")
                                .build(),
                        VibrationModels.DocumentSummary.builder()
                                .documentId("speed-1")
                                .kbId("kb-1")
                                .filename("generator_speed.mat")
                                .signalRole("SPEED")
                                .referenceShaftHint("HSS")
                                .build()
                ))
        ));

        assertEquals("vib-1", workspace.getSelectedDocumentId());
        assertEquals("speed-1", workspace.getSelectedSpeedDocumentId());
        assertEquals("HSS", workspace.getReferenceShaft());

        WorkflowStepPlan plan2 = workflow.nextPlan();
        assertEquals("LOAD_PARAMETER_CONTEXT", plan2.state());

        workflow.onToolResponses(response("KnowledgeTool", "{\"summary\":\"bearing parameters loaded\"}"));

        WorkflowStepPlan plan3 = workflow.nextPlan();
        assertEquals("RUN_BASE_ANALYSIS", plan3.state());

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setCrestFactor(5.1);
        basicStats.setKurtosis(4.9);

        workflow.onToolResponses(response(
                "analyzeVibrationSpectrum",
                objectMapper.writeValueAsString(
                        VibrationModels.SpectrumAnalysis.builder()
                                .documentId("vib-1")
                                .filename("bearing_vibration.mat")
                                .basicStats(basicStats)
                                .highFrequencyEnergyRatio(0.52)
                                .build()
                )
        ));

        WorkflowStepPlan plan4 = workflow.nextPlan();
        assertEquals("RUN_ADVANCED_ANALYSIS", plan4.state());
        assertTrue(plan4.allowedToolNames().contains("analyzeSpeedSignal"));
        assertTrue(plan4.allowedToolNames().contains("analyzeOrderSpectrum"));
        assertTrue(plan4.allowedToolNames().contains("matchWindTurbineReferenceProfile"));
        assertTrue(Boolean.TRUE.equals(workspace.getAdvancedAnalysisRequired()));

        workflow.onToolResponses(response(
                "analyzeEnvelopeSpectrum",
                objectMapper.writeValueAsString(
                        VibrationModels.EnvelopeSpectrumAnalysis.builder()
                                .documentId("vib-1")
                                .filename("bearing_vibration.mat")
                                .bandHint("8k-10k")
                                .build()
                )
        ));

        WorkflowStepPlan planAfterEnvelope = workflow.nextPlan();
        assertEquals("RUN_ADVANCED_ANALYSIS", planAfterEnvelope.state());
        assertTrue(Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted()));
        assertFalse(Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted()));

        workflow.onToolResponses(response(
                "analyzeSpeedSignal",
                objectMapper.writeValueAsString(
                        VibrationModels.SpeedSignalAnalysis.builder()
                                .documentId("speed-1")
                                .filename("generator_speed.mat")
                                .averageRpm(1800.0)
                                .build()
                )
        ));

        WorkflowStepPlan plan5 = workflow.nextPlan();
        assertEquals("GENERATE_DIAGNOSIS", plan5.state());
        assertTrue(Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted()));
        assertEquals(1800.0, workspace.getReferenceRpm(), 1e-6);
        assertFalse(workflow.isFinished());

        workflow.onAssistantResponse(AssistantMessage.builder().content("diagnosis completed").build());
        assertTrue(workflow.isFinished());
        assertEquals(DiagnosisWorkflowState.DONE, workspace.getCurrentState());
    }

    @Test
    void shouldResolveDocumentChoiceFromRecentUserReply() throws Exception {
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-select", "analyze D7", "kb-1");
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-select", "agent-1");
        runtimeState.setRecentMessages(List.of(
                RecentMessageSnapshot.builder()
                        .messageId("user-1")
                        .role(ChatMessageDTO.RoleType.USER)
                        .content("D7")
                        .build()
        ));

        WindTurbineBearingWorkflow workflow = new WindTurbineBearingWorkflow(
                objectMapper,
                new DiagnosisWorkspaceCache(new AgentMetrics(new SimpleMeterRegistry())),
                AgentDTO.RuntimeCacheOptions.defaultOptions(),
                List.of(KnowledgeBaseDTO.builder().id("kb-1").name("wind-turbine-knowledge").build()),
                Set.of("listVibrationDocuments", "KnowledgeTool"),
                true,
                runtimeState,
                workspace
        );

        workflow.onToolResponses(response(
                "listVibrationDocuments",
                objectMapper.writeValueAsString(List.of(
                        VibrationModels.DocumentSummary.builder()
                                .documentId("doc-d7")
                                .kbId("kb-1")
                                .filename("D7.mat")
                                .signalRole("MIXED")
                                .hasVibrationSignal(true)
                                .hasSpeedSignal(true)
                                .defaultSpeedSignalName("Speed")
                                .build(),
                        VibrationModels.DocumentSummary.builder()
                                .documentId("doc-motor")
                                .kbId("kb-1")
                                .filename("motor_signal.mat")
                                .signalRole("VIBRATION")
                                .build()
                ))
        ));

        WorkflowStepPlan plan = workflow.nextPlan();
        assertEquals("LOAD_PARAMETER_CONTEXT", plan.state());
        assertEquals("doc-d7", workspace.getSelectedDocumentId());
        assertEquals("D7.mat", workspace.getSelectedDocumentName());
        assertEquals("doc-d7", workspace.getSelectedSpeedDocumentId());
    }

    @Test
    void shouldSkipSpeedPathWhenDisabled() throws Exception {
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-no-speed", "analyze without speed", "kb-1");
        workspace.setSelectedSpeedDocumentId("stale-speed");
        workspace.setSelectedSpeedDocumentName("stale-speed.mat");
        workspace.setReferenceShaft("HSS");
        workspace.setReferenceRpm(1800.0);
        workspace.setSpeedAnalysisCompleted(true);
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-no-speed", "agent-1");
        WindTurbineBearingWorkflow workflow = new WindTurbineBearingWorkflow(
                objectMapper,
                new DiagnosisWorkspaceCache(new AgentMetrics(new SimpleMeterRegistry())),
                AgentDTO.RuntimeCacheOptions.defaultOptions(),
                List.of(KnowledgeBaseDTO.builder().id("kb-1").name("wind-turbine-knowledge").build()),
                Set.of(
                        "listVibrationDocuments",
                        "KnowledgeTool",
                        "analyzeVibrationSpectrum",
                        "analyzeEnvelopeSpectrum",
                        "analyzeSpeedSignal",
                        "analyzeOrderSpectrum",
                        "matchWindTurbineReferenceProfile",
                        "calculateBearingCharacteristicFrequencies"
                ),
                false,
                runtimeState,
                workspace
        );

        assertEquals(null, workspace.getSelectedSpeedDocumentId());
        assertEquals(null, workspace.getReferenceShaft());
        assertEquals(null, workspace.getReferenceRpm());
        assertFalse(Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted()));

        WorkflowStepPlan plan1 = workflow.nextPlan();
        assertEquals("SELECT_DOCUMENT", plan1.state());

        workflow.onToolResponses(response(
                "listVibrationDocuments",
                objectMapper.writeValueAsString(List.of(
                        VibrationModels.DocumentSummary.builder()
                                .documentId("vib-1")
                                .kbId("kb-1")
                                .filename("bearing_vibration.mat")
                                .signalRole("VIBRATION")
                                .build(),
                        VibrationModels.DocumentSummary.builder()
                                .documentId("speed-1")
                                .kbId("kb-1")
                                .filename("generator_speed.mat")
                                .signalRole("SPEED")
                                .referenceShaftHint("HSS")
                                .build()
                ))
        ));

        assertEquals("vib-1", workspace.getSelectedDocumentId());
        assertEquals(null, workspace.getSelectedSpeedDocumentId());

        WorkflowStepPlan plan2 = workflow.nextPlan();
        assertEquals("LOAD_PARAMETER_CONTEXT", plan2.state());

        workflow.onToolResponses(response("KnowledgeTool", "{\"summary\":\"bearing parameters loaded\"}"));

        WorkflowStepPlan plan3 = workflow.nextPlan();
        assertEquals("RUN_BASE_ANALYSIS", plan3.state());

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setCrestFactor(5.0);
        basicStats.setKurtosis(4.8);

        workflow.onToolResponses(response(
                "analyzeVibrationSpectrum",
                objectMapper.writeValueAsString(
                        VibrationModels.SpectrumAnalysis.builder()
                                .documentId("vib-1")
                                .filename("bearing_vibration.mat")
                                .basicStats(basicStats)
                                .highFrequencyEnergyRatio(0.51)
                                .build()
                )
        ));

        WorkflowStepPlan plan4 = workflow.nextPlan();
        assertEquals("RUN_ADVANCED_ANALYSIS", plan4.state());
        assertTrue(plan4.allowedToolNames().contains("analyzeEnvelopeSpectrum"));
        assertFalse(plan4.allowedToolNames().contains("analyzeSpeedSignal"));
        assertFalse(plan4.allowedToolNames().contains("analyzeOrderSpectrum"));
        assertFalse(plan4.allowedToolNames().contains("matchWindTurbineReferenceProfile"));

        workflow.onToolResponses(response(
                "analyzeEnvelopeSpectrum",
                objectMapper.writeValueAsString(
                        VibrationModels.EnvelopeSpectrumAnalysis.builder()
                                .documentId("vib-1")
                                .filename("bearing_vibration.mat")
                                .bandHint("8k-10k")
                                .build()
                )
        ));

        WorkflowStepPlan plan5 = workflow.nextPlan();
        assertEquals("GENERATE_DIAGNOSIS", plan5.state());
    }

    @Test
    void shouldAutoAdvanceToDiagnosisWhenAdvancedNodeReturnsTextOnly() throws Exception {
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-fallback", "fallback to report", "kb-1");
        workspace.setCurrentState(DiagnosisWorkflowState.RUN_ADVANCED_ANALYSIS);
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-fallback", "agent-1");
        WindTurbineBearingWorkflow workflow = new WindTurbineBearingWorkflow(
                objectMapper,
                new DiagnosisWorkspaceCache(new AgentMetrics(new SimpleMeterRegistry())),
                AgentDTO.RuntimeCacheOptions.defaultOptions(),
                List.of(KnowledgeBaseDTO.builder().id("kb-1").name("wind-turbine-knowledge").build()),
                Set.of(
                        "listVibrationDocuments",
                        "KnowledgeTool",
                        "analyzeVibrationSpectrum",
                        "analyzeEnvelopeSpectrum"
                ),
                false,
                runtimeState,
                workspace
        );

        WorkflowStepPlan advancedPlan = workflow.nextPlan();
        assertEquals("RUN_ADVANCED_ANALYSIS", advancedPlan.state());

        workflow.onAssistantResponse(AssistantMessage.builder().content("已具备足够证据，可以生成报告。").build());

        WorkflowStepPlan diagnosisPlan = workflow.nextPlan();
        assertEquals("GENERATE_DIAGNOSIS", diagnosisPlan.state());
        assertFalse(workflow.isFinished());
        assertFalse(Boolean.TRUE.equals(workspace.getAwaitingUserInput()));
    }

    private ToolResponseProcessingResult response(String toolName, String payload) {
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse("call-1", toolName, payload);

        return ToolResponseProcessingResult.builder()
                .processedMessage(null)
                .responses(List.of(
                        ToolResponseProcessingResult.ProcessedToolResponse.builder()
                                .rawResponse(toolResponse)
                                .processedResponse(toolResponse)
                                .compressed(false)
                                .compressionModel(null)
                                .rawContentLength(payload.length())
                                .processedContentLength(payload.length())
                                .build()
                ))
                .build();
    }
}
