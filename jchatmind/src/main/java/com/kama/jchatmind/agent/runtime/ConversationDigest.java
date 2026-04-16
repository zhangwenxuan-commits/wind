package com.kama.jchatmind.agent.runtime;

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
public class ConversationDigest {

    private String userIntent;
    private String latestUserReplySummary;
    private String pendingQuestion;
    private List<String> confirmedFacts;
    private List<String> constraints;
    private String reportPreference;
    private String latestWorkflowSummary;
    private String lastProcessedUserMessageId;
    private String lastProcessedAssistantMessageId;

    public static ConversationDigest create() {
        return ConversationDigest.builder()
                .confirmedFacts(new ArrayList<>())
                .constraints(new ArrayList<>())
                .build();
    }

    public void addConfirmedFact(String fact) {
        if (fact == null || fact.isBlank()) {
            return;
        }
        if (confirmedFacts == null) {
            confirmedFacts = new ArrayList<>();
        }
        if (!confirmedFacts.contains(fact)) {
            confirmedFacts.add(fact);
        }
    }

    public void addConstraint(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return;
        }
        if (constraints == null) {
            constraints = new ArrayList<>();
        }
        if (!constraints.contains(constraint)) {
            constraints.add(constraint);
        }
    }
}
