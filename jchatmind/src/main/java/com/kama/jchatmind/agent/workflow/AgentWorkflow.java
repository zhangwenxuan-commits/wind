package com.kama.jchatmind.agent.workflow;

import com.kama.jchatmind.agent.runtime.ToolResponseProcessingResult;
import org.springframework.ai.chat.messages.AssistantMessage;

public interface AgentWorkflow {

    WorkflowStepPlan nextPlan();

    void onAssistantResponse(AssistantMessage assistantMessage);

    void onToolResponses(ToolResponseProcessingResult processingResult);

    boolean isFinished();
}
