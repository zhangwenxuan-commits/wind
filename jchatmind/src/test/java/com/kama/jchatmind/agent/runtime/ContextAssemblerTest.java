package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.agent.workflow.WorkflowStepPlan;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkflowState;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

    @Test
    void shouldBuildPromptFromWorkspaceDigestAndRecentMessages() {
        ContextAssembler assembler = new ContextAssembler();
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-1", "agent-1");

        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-1", "分析风机轴承", "kb-1");
        workspace.setCurrentState(DiagnosisWorkflowState.RUN_ADVANCED_ANALYSIS);
        workspace.setSelectedDocumentName("bearing.mat");
        workspace.setCrestFactor(5.1);
        runtimeState.setDiagnosisWorkspace(workspace);

        ConversationDigest digest = ConversationDigest.create();
        digest.setUserIntent("分析主轴轴承故障");
        digest.setLatestUserReplySummary("用户确认分析对象为主轴轴承");
        digest.setPendingQuestion("请确认轴承位置");
        runtimeState.setConversationDigest(digest);

        runtimeState.setRecentMessages(List.of(
                RecentMessageSnapshot.builder()
                        .messageId("a-1")
                        .role(ChatMessageDTO.RoleType.ASSISTANT)
                        .content("请确认分析的是哪个轴承")
                        .build(),
                RecentMessageSnapshot.builder()
                        .messageId("u-1")
                        .role(ChatMessageDTO.RoleType.USER)
                        .content("主轴轴承")
                        .build()
        ));

        WorkflowStepPlan plan = WorkflowStepPlan.builder()
                .state("RUN_ADVANCED_ANALYSIS")
                .statusText("Step 4/5")
                .systemPrompt("Run advanced analysis")
                .allowedToolNames(Set.of("analyzeEnvelopeSpectrum"))
                .build();

        String systemPrompt = assembler.buildSystemPrompt("You are a diagnostic agent.", plan, runtimeState);
        List<Message> messages = assembler.buildSeedMessages(runtimeState);

        assertTrue(systemPrompt.contains("Agent Role"));
        assertTrue(systemPrompt.contains("Workspace Snapshot"));
        assertTrue(systemPrompt.contains("Conversation Digest"));
        assertTrue(systemPrompt.contains("bearing.mat"));
        assertTrue(systemPrompt.contains("分析主轴轴承故障"));

        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof AssistantMessage);
        assertTrue(messages.get(1) instanceof UserMessage);
    }
}
