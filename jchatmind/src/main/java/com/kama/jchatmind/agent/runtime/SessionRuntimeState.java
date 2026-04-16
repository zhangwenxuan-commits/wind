package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
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
public class SessionRuntimeState {

    private String sessionId;
    private String agentId;
    private String workflowType;
    private DiagnosisWorkspace diagnosisWorkspace;
    private ConversationDigest conversationDigest;
    private List<RecentMessageSnapshot> recentMessages;

    public static SessionRuntimeState create(String sessionId, String agentId) {
        return SessionRuntimeState.builder()
                .sessionId(sessionId)
                .agentId(agentId)
                .conversationDigest(ConversationDigest.create())
                .recentMessages(new ArrayList<>())
                .build();
    }

    public ConversationDigest ensureDigest() {
        if (conversationDigest == null) {
            conversationDigest = ConversationDigest.create();
        }
        return conversationDigest;
    }

    public List<RecentMessageSnapshot> ensureRecentMessages() {
        if (recentMessages == null) {
            recentMessages = new ArrayList<>();
        }
        return recentMessages;
    }
}
