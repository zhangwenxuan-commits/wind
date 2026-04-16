package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.agent.workflow.WorkflowStepPlan;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class ContextAssembler {

    public List<Message> buildSeedMessages(SessionRuntimeState runtimeState) {
        List<Message> messages = new ArrayList<>();
        if (runtimeState == null || runtimeState.getRecentMessages() == null) {
            return messages;
        }

        for (RecentMessageSnapshot snapshot : runtimeState.getRecentMessages()) {
            if (snapshot == null || !StringUtils.hasText(snapshot.getContent())) {
                continue;
            }
            if (snapshot.getRole() == ChatMessageDTO.RoleType.USER) {
                messages.add(new UserMessage(snapshot.getContent()));
            } else if (snapshot.getRole() == ChatMessageDTO.RoleType.ASSISTANT) {
                messages.add(AssistantMessage.builder().content(snapshot.getContent()).build());
            }
        }
        return messages;
    }

    public String buildSystemPrompt(String agentSystemPrompt, WorkflowStepPlan plan, SessionRuntimeState runtimeState) {
        StringBuilder prompt = new StringBuilder();
        appendSection(prompt, "Agent Role", agentSystemPrompt);
        appendSection(prompt, "Node Instructions", plan != null ? plan.systemPrompt() : null);
        appendSection(prompt, "Workspace Snapshot", renderWorkspace(runtimeState != null ? runtimeState.getDiagnosisWorkspace() : null));
        appendSection(prompt, "Conversation Digest", renderDigest(runtimeState != null ? runtimeState.getConversationDigest() : null));
        prompt.append("Context policy:\n");
        prompt.append("- Prioritize the workspace snapshot and conversation digest over omitted chat history.\n");
        prompt.append("- Recent raw messages are auxiliary context only.\n");
        prompt.append("- Do not invent missing facts that are not present in the workspace, digest, or tool results.\n");
        return prompt.toString().trim();
    }

    private void appendSection(StringBuilder prompt, String title, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (prompt.length() > 0) {
            prompt.append("\n\n");
        }
        prompt.append(title).append(":\n").append(content.trim());
    }

    private String renderWorkspace(DiagnosisWorkspace workspace) {
        if (workspace == null) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        addIfPresent(lines, "state", workspace.getCurrentState() != null ? workspace.getCurrentState().name() : null);
        addIfPresent(lines, "userGoal", workspace.getUserGoal());
        addIfPresent(lines, "selectedKbId", workspace.getSelectedKbId());
        addIfPresent(lines, "selectedDocumentId", workspace.getSelectedDocumentId());
        addIfPresent(lines, "selectedDocumentName", workspace.getSelectedDocumentName());
        addIfPresent(lines, "selectedSpeedDocumentId", workspace.getSelectedSpeedDocumentId());
        addIfPresent(lines, "selectedSpeedDocumentName", workspace.getSelectedSpeedDocumentName());
        addIfPresent(lines, "referenceShaft", workspace.getReferenceShaft());
        addIfPresent(lines, "referenceRpm", formatNumber(workspace.getReferenceRpm()));
        addIfPresent(lines, "parameterContextLoaded", boolText(workspace.getParameterContextLoaded()));
        addIfPresent(lines, "baseAnalysisCompleted", boolText(workspace.getBaseAnalysisCompleted()));
        addIfPresent(lines, "advancedAnalysisRequired", boolText(workspace.getAdvancedAnalysisRequired()));
        addIfPresent(lines, "advancedAnalysisCompleted", boolText(workspace.getAdvancedAnalysisCompleted()));
        addIfPresent(lines, "speedAnalysisCompleted", boolText(workspace.getSpeedAnalysisCompleted()));
        addIfPresent(lines, "orderSpectrumCompleted", boolText(workspace.getOrderSpectrumCompleted()));
        addIfPresent(lines, "referenceMatchEvaluated", boolText(workspace.getReferenceMatchEvaluated()));
        addIfPresent(lines, "referenceProfileMatched", boolText(workspace.getReferenceProfileMatched()));
        addIfPresent(lines, "bearingFrequenciesCalculated", boolText(workspace.getBearingFrequenciesCalculated()));
        addIfPresent(lines, "crestFactor", formatNumber(workspace.getCrestFactor()));
        addIfPresent(lines, "kurtosis", formatNumber(workspace.getKurtosis()));
        addIfPresent(lines, "highFrequencyRatio", formatNumber(workspace.getHighFrequencyRatio()));
        addIfPresent(lines, "lastDecisionReason", workspace.getLastDecisionReason());
        if (workspace.getCandidateDocuments() != null && !workspace.getCandidateDocuments().isEmpty()) {
            String candidates = workspace.getCandidateDocuments().stream()
                    .map(this::renderCandidateDocument)
                    .limit(4)
                    .collect(java.util.stream.Collectors.joining(" | "));
            addIfPresent(lines, "candidateDocuments", candidates);
        }
        if (workspace.getEvidenceNotes() != null && !workspace.getEvidenceNotes().isEmpty()) {
            List<String> recentEvidence = workspace.getEvidenceNotes().size() <= 4
                    ? workspace.getEvidenceNotes()
                    : workspace.getEvidenceNotes().subList(workspace.getEvidenceNotes().size() - 4, workspace.getEvidenceNotes().size());
            lines.add("evidenceNotes: " + String.join(" | ", recentEvidence));
        }
        return String.join("\n", lines);
    }

    private String renderDigest(ConversationDigest digest) {
        if (digest == null) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        addIfPresent(lines, "userIntent", digest.getUserIntent());
        addIfPresent(lines, "latestUserReplySummary", digest.getLatestUserReplySummary());
        addIfPresent(lines, "pendingQuestion", digest.getPendingQuestion());
        addIfPresent(lines, "latestWorkflowSummary", digest.getLatestWorkflowSummary());
        addIfPresent(lines, "reportPreference", digest.getReportPreference());
        if (digest.getConfirmedFacts() != null && !digest.getConfirmedFacts().isEmpty()) {
            lines.add("confirmedFacts: " + String.join(" | ", digest.getConfirmedFacts()));
        }
        if (digest.getConstraints() != null && !digest.getConstraints().isEmpty()) {
            lines.add("constraints: " + String.join(" | ", digest.getConstraints()));
        }
        return String.join("\n", lines);
    }

    private void addIfPresent(List<String> lines, String key, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(key + ": " + value);
        }
    }

    private String formatNumber(Double value) {
        return value == null ? null : String.format("%.4f", value);
    }

    private String boolText(Boolean value) {
        return value == null ? null : value.toString();
    }

    private String renderCandidateDocument(VibrationModels.DocumentSummary document) {
        if (document == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(document.getFilename());
        if (StringUtils.hasText(document.getSignalRole())) {
            builder.append("(").append(document.getSignalRole()).append(")");
        }
        return builder.toString();
    }
}
