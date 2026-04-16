package com.kama.jchatmind.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspace;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisWorkspaceCache;
import com.kama.jchatmind.agent.workflow.vibration.WindTurbineBearingWorkflow;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AgentWorkflowFactory {

    private final ObjectMapper objectMapper;
    private final DiagnosisWorkspaceCache workspaceCache;
    private final boolean speedPathEnabled;

    public AgentWorkflowFactory(
            ObjectMapper objectMapper,
            DiagnosisWorkspaceCache workspaceCache,
            @Value("${agent.workflow.vibration.speed-path-enabled:true}") boolean speedPathEnabled
    ) {
        this.objectMapper = objectMapper;
        this.workspaceCache = workspaceCache;
        this.speedPathEnabled = speedPathEnabled;
    }

    public AgentWorkflow create(
            AgentDTO agentConfig,
            SessionRuntimeState runtimeState,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<ToolCallback> toolCallbacks
    ) {
        Set<String> toolNames = toolCallbacks.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toSet());

        String defaultKbId = knowledgeBases.size() == 1 ? knowledgeBases.get(0).getId() : null;
        AgentDTO.RuntimeCacheOptions runtimeCacheOptions = agentConfig.getChatOptions().resolveRuntimeCache();
        DiagnosisWorkspace workspace = runtimeState.getDiagnosisWorkspace();
        if (workspace == null) {
            String userGoal = runtimeState.getConversationDigest() != null
                    ? runtimeState.getConversationDigest().getUserIntent()
                    : null;
            workspace = DiagnosisWorkspace.create(runtimeState.getSessionId(), userGoal, defaultKbId);
            runtimeState.setDiagnosisWorkspace(workspace);
        }
        if (!StringUtils.hasText(workspace.getUserGoal())
                && runtimeState.getConversationDigest() != null
                && StringUtils.hasText(runtimeState.getConversationDigest().getUserIntent())) {
            workspace.setUserGoal(runtimeState.getConversationDigest().getUserIntent());
        }
        if (!StringUtils.hasText(workspace.getSelectedKbId()) && StringUtils.hasText(defaultKbId)) {
            workspace.setSelectedKbId(defaultKbId);
        }
        runtimeState.setWorkflowType("wind-turbine-bearing");

        return new WindTurbineBearingWorkflow(
                objectMapper,
                workspaceCache,
                runtimeCacheOptions,
                knowledgeBases,
                toolNames,
                speedPathEnabled,
                runtimeState,
                workspace
        );
    }
}
