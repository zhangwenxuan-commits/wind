package com.kama.jchatmind.agent.workflow;

import lombok.Builder;

import java.util.Set;

@Builder
public record WorkflowStepPlan(
        String state,
        String statusText,
        String systemPrompt,
        Set<String> allowedToolNames
) {

    public boolean allowsToolCalls() {
        return allowedToolNames != null && !allowedToolNames.isEmpty();
    }
}
