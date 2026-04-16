package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationDigestReducerTest {

    @Test
    void shouldSyncRecentMessagesAndWorkspaceFacts() {
        ConversationDigestReducer reducer = new ConversationDigestReducer();
        SessionRuntimeState runtimeState = SessionRuntimeState.create("session-1", "agent-1");
        DiagnosisWorkspace workspace = DiagnosisWorkspace.create("session-1", "分析风机轴承", "kb-1");
        runtimeState.setDiagnosisWorkspace(workspace);

        reducer.syncRecentMessages(runtimeState, List.of(
                chatMessage("u-1", com.kama.jchatmind.model.dto.ChatMessageDTO.RoleType.USER, "请分析这个风机轴承振动文件"),
                chatMessage("a-1", com.kama.jchatmind.model.dto.ChatMessageDTO.RoleType.ASSISTANT, "请确认是主轴轴承还是高速轴轴承"),
                chatMessage("u-2", com.kama.jchatmind.model.dto.ChatMessageDTO.RoleType.USER, "主轴轴承")
        ));

        assertEquals("请分析这个风机轴承振动文件", runtimeState.getConversationDigest().getUserIntent());
        assertEquals("主轴轴承", runtimeState.getConversationDigest().getLatestUserReplySummary());
        assertNull(runtimeState.getConversationDigest().getPendingQuestion());

        workspace.setSelectedDocumentName("bearing.mat");
        workspace.setParameterContextLoaded(true);
        workspace.setBaseAnalysisCompleted(true);
        workspace.setLastDecisionReason("Base spectrum indicates impact or high-frequency abnormality.");

        reducer.onWorkspaceUpdated(runtimeState);

        assertTrue(runtimeState.getConversationDigest().getConfirmedFacts().contains("selected document: bearing.mat"));
        assertTrue(runtimeState.getConversationDigest().getConfirmedFacts().contains("parameter context loaded"));
        assertEquals("Base spectrum indicates impact or high-frequency abnormality.", runtimeState.getConversationDigest().getLatestWorkflowSummary());
    }

    private com.kama.jchatmind.model.dto.ChatMessageDTO chatMessage(
            String id,
            com.kama.jchatmind.model.dto.ChatMessageDTO.RoleType role,
            String content
    ) {
        return com.kama.jchatmind.model.dto.ChatMessageDTO.builder()
                .id(id)
                .role(role)
                .content(content)
                .sessionId("session-1")
                .build();
    }
}
