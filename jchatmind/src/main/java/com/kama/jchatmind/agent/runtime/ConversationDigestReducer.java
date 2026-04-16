package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationDigestReducer {

    private static final int MAX_SUMMARY_CHARS = 220;
    private static final int MAX_FACTS = 6;

    public void syncRecentMessages(SessionRuntimeState runtimeState, List<ChatMessageDTO> recentRawMessages) {
        List<RecentMessageSnapshot> snapshots = runtimeState.ensureRecentMessages();
        snapshots.clear();
        if (recentRawMessages != null) {
            for (ChatMessageDTO message : recentRawMessages) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                snapshots.add(RecentMessageSnapshot.builder()
                        .messageId(message.getId())
                        .role(message.getRole())
                        .content(message.getContent())
                        .build());
            }
        }

        ConversationDigest digest = runtimeState.ensureDigest();
        ChatMessageDTO firstUserMessage = firstMessageByRole(recentRawMessages, ChatMessageDTO.RoleType.USER);
        if (!StringUtils.hasText(digest.getUserIntent()) && firstUserMessage != null) {
            digest.setUserIntent(summarize(firstUserMessage.getContent(), MAX_SUMMARY_CHARS));
        }
        ChatMessageDTO latestUserMessage = latestMessageByRole(recentRawMessages, ChatMessageDTO.RoleType.USER);
        if (latestUserMessage != null && !latestUserMessage.getId().equals(digest.getLastProcessedUserMessageId())) {
            String summary = summarize(latestUserMessage.getContent(), MAX_SUMMARY_CHARS);
            if (firstUserMessage == null || !latestUserMessage.getId().equals(firstUserMessage.getId())) {
                digest.setLatestUserReplySummary(summary);
            }
            digest.setPendingQuestion(null);
            digest.setLastProcessedUserMessageId(latestUserMessage.getId());
        }
    }

    public void onAssistantMessage(
            SessionRuntimeState runtimeState,
            String messageId,
            String content,
            boolean hasToolCalls,
            boolean finalNode
    ) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(messageId)) {
            return;
        }

        ConversationDigest digest = runtimeState.ensureDigest();
        if (messageId.equals(digest.getLastProcessedAssistantMessageId())) {
            return;
        }

        String summary = summarize(content, MAX_SUMMARY_CHARS);
        if (!hasToolCalls && !finalNode) {
            digest.setPendingQuestion(summary);
        }
        if (finalNode) {
            digest.setPendingQuestion(null);
            digest.setLatestWorkflowSummary(summary);
        }
        digest.setLastProcessedAssistantMessageId(messageId);
    }

    public void onWorkspaceUpdated(SessionRuntimeState runtimeState) {
        DiagnosisWorkspace workspace = runtimeState.getDiagnosisWorkspace();
        if (workspace == null) {
            return;
        }

        ConversationDigest digest = runtimeState.ensureDigest();
        List<String> facts = new ArrayList<>();
        if (StringUtils.hasText(workspace.getSelectedDocumentName())) {
            facts.add("selected document: " + workspace.getSelectedDocumentName());
        }
        if (StringUtils.hasText(workspace.getSelectedSpeedDocumentName())) {
            facts.add("selected speed document: " + workspace.getSelectedSpeedDocumentName());
        }
        if (Boolean.TRUE.equals(workspace.getParameterContextLoaded())) {
            facts.add("parameter context loaded");
        }
        if (Boolean.TRUE.equals(workspace.getBaseAnalysisCompleted())) {
            facts.add("base spectrum analysis completed");
        }
        if (Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted())) {
            facts.add("advanced analysis completed");
        }
        if (Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted())) {
            facts.add("speed signal analyzed");
        }
        if (Boolean.TRUE.equals(workspace.getOrderSpectrumCompleted())) {
            facts.add("order spectrum completed");
        }
        if (Boolean.TRUE.equals(workspace.getReferenceMatchEvaluated())) {
            facts.add(Boolean.TRUE.equals(workspace.getReferenceProfileMatched())
                    ? "reference frequency match found"
                    : "reference frequency match evaluated");
        }
        if (Boolean.TRUE.equals(workspace.getBearingFrequenciesCalculated())) {
            facts.add("bearing characteristic frequencies calculated");
        }

        digest.setConfirmedFacts(trimFacts(facts));
        if (StringUtils.hasText(workspace.getLastDecisionReason())) {
            digest.setLatestWorkflowSummary(workspace.getLastDecisionReason());
        }
    }

    private List<String> trimFacts(List<String> facts) {
        if (facts.size() <= MAX_FACTS) {
            return facts;
        }
        return new ArrayList<>(facts.subList(facts.size() - MAX_FACTS, facts.size()));
    }

    private ChatMessageDTO latestMessageByRole(List<ChatMessageDTO> messages, ChatMessageDTO.RoleType role) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageDTO message = messages.get(i);
            if (message != null && message.getRole() == role && StringUtils.hasText(message.getContent())) {
                return message;
            }
        }
        return null;
    }

    private ChatMessageDTO firstMessageByRole(List<ChatMessageDTO> messages, ChatMessageDTO.RoleType role) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (ChatMessageDTO message : messages) {
            if (message != null && message.getRole() == role && StringUtils.hasText(message.getContent())) {
                return message;
            }
        }
        return null;
    }

    private String summarize(String raw, int maxChars) {
        String normalized = raw == null ? "" : raw.replace("\r", " ").replace("\n", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }
}
