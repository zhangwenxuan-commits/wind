package com.kama.jchatmind.agent;

import com.kama.jchatmind.agent.runtime.ContextAssembler;
import com.kama.jchatmind.agent.runtime.ConversationDigestReducer;
import com.kama.jchatmind.agent.runtime.RecentMessageSnapshot;
import com.kama.jchatmind.agent.runtime.SessionMemoryCache;
import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import com.kama.jchatmind.agent.runtime.SessionRuntimeStateStore;
import com.kama.jchatmind.agent.runtime.ToolResponseProcessingResult;
import com.kama.jchatmind.agent.runtime.ToolResultProcessor;
import com.kama.jchatmind.agent.workflow.AgentWorkflow;
import com.kama.jchatmind.agent.workflow.WorkflowStepPlan;
import com.kama.jchatmind.agent.workflow.vibration.DiagnosisReportComposer;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.SseService;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class JChatMind {

    private static final int MAX_STEPS = 20;
    private static final int MAX_RECENT_RAW_MESSAGES = 4;

    private final String agentId;
    private final String name;
    private final String description;
    private final String systemPrompt;
    private final String agentModel;
    private final ChatClient chatClient;
    private final List<ToolCallback> availableTools;
    private final List<KnowledgeBaseDTO> availableKbs;
    private final ToolCallingManager toolCallingManager;
    private final ChatMemory chatMemory;
    private final String chatSessionId;
    private final ChatOptions modelChatOptions;
    private final SseService sseService;
    private final ChatMessageConverter chatMessageConverter;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final AgentDTO.ChatOptions agentChatOptions;
    private final AgentDTO.RuntimeCacheOptions runtimeCacheOptions;
    private final int maxMessages;
    private final SessionMemoryCache sessionMemoryCache;
    private final ToolResultProcessor toolResultProcessor;
    private final AgentMetrics agentMetrics;
    private final AgentWorkflow workflow;
    private final SessionRuntimeState runtimeState;
    private final SessionRuntimeStateStore sessionRuntimeStateStore;
    private final ConversationDigestReducer conversationDigestReducer;
    private final ContextAssembler contextAssembler;

    private AgentState agentState;
    private ChatResponse lastChatResponse;

    private final List<ChatMessageDTO> pendingChatMessages = new ArrayList<>();

    public JChatMind(
            String agentId,
            String name,
            String description,
            String systemPrompt,
            String agentModel,
            ChatClient chatClient,
            AgentDTO.ChatOptions agentChatOptions,
            List<Message> seedMessages,
            List<ToolCallback> availableTools,
            List<KnowledgeBaseDTO> availableKbs,
            AgentWorkflow workflow,
            SessionRuntimeState runtimeState,
            String chatSessionId,
            SseService sseService,
            ChatMessageFacadeService chatMessageFacadeService,
            ChatMessageConverter chatMessageConverter,
            SessionMemoryCache sessionMemoryCache,
            ToolResultProcessor toolResultProcessor,
            AgentMetrics agentMetrics,
            SessionRuntimeStateStore sessionRuntimeStateStore,
            ConversationDigestReducer conversationDigestReducer,
            ContextAssembler contextAssembler
    ) {
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.agentModel = agentModel;
        this.chatClient = chatClient;
        this.availableTools = availableTools;
        this.availableKbs = availableKbs;
        this.workflow = workflow;
        this.runtimeState = runtimeState;
        this.chatSessionId = chatSessionId;
        this.sseService = sseService;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.sessionMemoryCache = sessionMemoryCache;
        this.toolResultProcessor = toolResultProcessor;
        this.agentMetrics = agentMetrics;
        this.sessionRuntimeStateStore = sessionRuntimeStateStore;
        this.conversationDigestReducer = conversationDigestReducer;
        this.contextAssembler = contextAssembler;
        this.agentState = AgentState.IDLE;
        this.agentChatOptions = agentChatOptions != null ? agentChatOptions : AgentDTO.ChatOptions.defaultOptions();
        this.runtimeCacheOptions = this.agentChatOptions.resolveRuntimeCache();
        this.maxMessages = this.agentChatOptions.resolveMessageLength();

        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(this.maxMessages)
                .build();
        this.chatMemory.add(chatSessionId, seedMessages);

        this.modelChatOptions = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("[ToolCalling] no tool calls");
            return;
        }
        String logMessage = IntStream.range(0, toolCalls.size())
                .mapToObj(i -> {
                    AssistantMessage.ToolCall call = toolCalls.get(i);
                    return String.format(
                            "[ToolCalling #%d]%n- name      : %s%n- arguments : %s",
                            i + 1,
                            call.name(),
                            call.arguments()
                    );
                })
                .collect(Collectors.joining("\n\n"));
        log.info("\n========== Tool Calling ==========\n{}\n=================================\n", logMessage);
    }

    private ChatMessageDTO saveAssistantMessage(AssistantMessage assistantMessage) {
        ChatMessageDTO chatMessageDTO = ChatMessageDTO.builder()
                .role(ChatMessageDTO.RoleType.ASSISTANT)
                .content(assistantMessage.getText())
                .sessionId(this.chatSessionId)
                .metadata(ChatMessageDTO.MetaData.builder()
                        .toolCalls(assistantMessage.getToolCalls())
                        .build())
                .build();

        CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
        chatMessageDTO.setId(chatMessage.getChatMessageId());
        pendingChatMessages.add(chatMessageDTO);
        return chatMessageDTO;
    }

    private void saveToolMessages(ToolResponseProcessingResult processingResult) {
        for (ToolResponseProcessingResult.ProcessedToolResponse processedToolResponse : processingResult.getResponses()) {
            ToolResponseMessage.ToolResponse rawResponse = processedToolResponse.getRawResponse();
            ToolResponseMessage.ToolResponse processedResponse = processedToolResponse.getProcessedResponse();

            ChatMessageDTO chatMessageDTO = ChatMessageDTO.builder()
                    .role(ChatMessageDTO.RoleType.TOOL)
                    .content(rawResponse.responseData())
                    .sessionId(this.chatSessionId)
                    .metadata(ChatMessageDTO.MetaData.builder()
                            .toolResponse(rawResponse)
                            .processedToolResponse(processedResponse)
                            .toolResponseCompressed(processedToolResponse.isCompressed())
                            .toolResponseCompressionModel(processedToolResponse.getCompressionModel())
                            .rawContentLength(processedToolResponse.getRawContentLength())
                            .processedContentLength(processedToolResponse.getProcessedContentLength())
                            .build())
                    .build();

            CreateChatMessageResponse chatMessage = chatMessageFacadeService.createChatMessage(chatMessageDTO);
            chatMessageDTO.setId(chatMessage.getChatMessageId());
            pendingChatMessages.add(chatMessageDTO);
        }
    }

    private void refreshPendingMessages() {
        for (ChatMessageDTO message : pendingChatMessages) {
            ChatMessageVO vo = chatMessageConverter.toVO(message);
            SseMessage sseMessage = SseMessage.builder()
                    .type(SseMessage.Type.AI_GENERATED_CONTENT)
                    .payload(SseMessage.Payload.builder()
                            .message(vo)
                            .build())
                    .metadata(SseMessage.Metadata.builder()
                            .chatMessageId(message.getId())
                            .build())
                    .build();
            sseService.send(this.chatSessionId, sseMessage);
        }
        pendingChatMessages.clear();
    }

    private void sendStatus(SseMessage.Type type, String statusText, Boolean done) {
        sseService.send(
                this.chatSessionId,
                SseMessage.builder()
                        .type(type)
                        .payload(SseMessage.Payload.builder()
                                .statusText(statusText)
                                .done(done)
                                .build())
                        .build()
        );
    }

    private List<ToolCallback> resolveToolCallbacks(WorkflowStepPlan plan) {
        if (plan == null || !plan.allowsToolCalls()) {
            return List.of();
        }

        // Workflow step plan provides the hard whitelist for the current node.
        Set<String> allowedToolNames = plan.allowedToolNames();
        return availableTools.stream()
                .filter(callback -> allowedToolNames.contains(callback.getToolDefinition().name()))
                .toList();
    }

    private List<String> currentToolCallNames() {
        if (this.lastChatResponse == null || this.lastChatResponse.getResult() == null) {
            return List.of();
        }
        AssistantMessage output = this.lastChatResponse.getResult().getOutput();
        if (output == null || output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
            return List.of();
        }
        return output.getToolCalls().stream()
                .map(AssistantMessage.ToolCall::name)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<ToolCallback> resolveExecutionToolCallbacks(WorkflowStepPlan plan) {
        List<ToolCallback> activeTools = new ArrayList<>(resolveToolCallbacks(plan));
        List<String> requestedToolNames = currentToolCallNames();
        if (requestedToolNames.isEmpty()) {
            return activeTools;
        }

        Set<String> currentNames = activeTools.stream()
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missingToolNames = requestedToolNames.stream()
                .filter(requested -> currentNames.stream().noneMatch(registered -> matchesToolName(requested, registered)))
                .toList();
        if (missingToolNames.isEmpty()) {
            return activeTools;
        }

        for (String missingToolName : missingToolNames) {
            ToolCallback callback = findToolCallback(missingToolName);
            if (callback == null) {
                continue;
            }
            String callbackName = callback.getToolDefinition().name();
            if (currentNames.add(callbackName)) {
                activeTools.add(callback);
            }
        }

        List<String> unresolvedToolNames = requestedToolNames.stream()
                .filter(requested -> currentNames.stream().noneMatch(registered -> matchesToolName(requested, registered)))
                .toList();
        if (unresolvedToolNames.isEmpty()) {
            log.warn("Execution tool callback set was supplemented. requested={}, resolved={}", requestedToolNames, currentNames);
            return activeTools;
        }

        log.warn("Falling back to all runtime tool callbacks. requested={}, unresolved={}, available={}",
                requestedToolNames,
                unresolvedToolNames,
                availableTools.stream().map(callback -> callback.getToolDefinition().name()).toList());
        return availableTools;
    }

    private ToolCallback findToolCallback(String requestedToolName) {
        for (ToolCallback callback : availableTools) {
            String registeredName = callback.getToolDefinition().name();
            if (matchesToolName(requestedToolName, registeredName)) {
                return callback;
            }
        }
        return null;
    }

    private boolean matchesToolName(String requestedToolName, String registeredToolName) {
        if (!StringUtils.hasText(requestedToolName) || !StringUtils.hasText(registeredToolName)) {
            return false;
        }
        if (requestedToolName.equals(registeredToolName)) {
            return true;
        }

        String normalizedRequested = normalizeToolName(requestedToolName);
        String normalizedRegistered = normalizeToolName(registeredToolName);
        if (normalizedRequested.equals(normalizedRegistered)) {
            return true;
        }

        return normalizedRegistered.startsWith(normalizedRequested)
                || normalizedRequested.startsWith(normalizedRegistered);
    }

    private String normalizeToolName(String toolName) {
        return toolName == null
                ? ""
                : toolName.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    private void persistRuntimeState() {
        if (runtimeState == null) {
            return;
        }
        sessionRuntimeStateStore.save(runtimeState, runtimeCacheOptions.getSessionMemoryTtlSeconds());
    }

    private void appendRecentRawMessage(ChatMessageDTO.RoleType role, String messageId, String content) {
        if (runtimeState == null || !StringUtils.hasText(content)) {
            return;
        }

        // Only keep a tiny raw-message tail for cross-turn language continuity.
        List<RecentMessageSnapshot> recentMessages = runtimeState.ensureRecentMessages();
        recentMessages.add(RecentMessageSnapshot.builder()
                .messageId(messageId)
                .role(role)
                .content(content)
                .build());
        while (recentMessages.size() > MAX_RECENT_RAW_MESSAGES) {
            recentMessages.remove(0);
        }
    }

    private boolean think(WorkflowStepPlan plan) {
        Timer.Sample sample = agentMetrics.startSample();
        List<ToolCallback> activeTools = resolveToolCallbacks(plan);
        List<Message> currentMessages = this.chatMemory.get(this.chatSessionId);
        agentMetrics.record("agent.context.message.count", currentMessages.size());
        agentMetrics.record("agent.context.char.count", estimateContextChars(currentMessages));

        try {
            // Cross-turn context is assembled from role prompt, node prompt,
            // runtime workspace, digest, and a very small raw-message tail.
            String effectiveSystemPrompt = contextAssembler.buildSystemPrompt(this.systemPrompt, plan, runtimeState);
            Prompt prompt = Prompt.builder()
                    .chatOptions(this.modelChatOptions)
                    .messages(currentMessages)
                    .build();

            this.lastChatResponse = this.chatClient
                    .prompt(prompt)
                    .system(effectiveSystemPrompt)
                    .toolCallbacks(activeTools.toArray(new ToolCallback[0]))
                    .call()
                    .chatClientResponse()
                    .chatResponse();

            Assert.notNull(lastChatResponse, "Last chat client response cannot be null");
            agentMetrics.recordTokenUsage("primary", resolveResponseModel(lastChatResponse), resolveUsage(lastChatResponse));

            AssistantMessage output = this.lastChatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
            boolean finalNode = "GENERATE_DIAGNOSIS".equals(plan.state()) || "DONE".equals(plan.state());

            ChatMessageDTO assistantMessage = saveAssistantMessage(output);
            conversationDigestReducer.onAssistantMessage(
                    runtimeState,
                    assistantMessage.getId(),
                    output.getText(),
                    hasToolCalls,
                    finalNode
            );
            if (!hasToolCalls) {
                appendRecentRawMessage(ChatMessageDTO.RoleType.ASSISTANT, assistantMessage.getId(), output.getText());
            }
            persistRuntimeState();

            refreshPendingMessages();
            logToolCalls(toolCalls);
            workflow.onAssistantResponse(output);

            return hasToolCalls;
        } finally {
            agentMetrics.stop(sample, "agent.llm.think");
        }
    }

    private void execute(WorkflowStepPlan plan) {
        Assert.notNull(this.lastChatResponse, "Last chat client response cannot be null");

        if (!this.lastChatResponse.hasToolCalls()) {
            return;
        }

        List<ToolCallback> activeTools = resolveExecutionToolCallbacks(plan);
        Prompt prompt = Prompt.builder()
                .messages(this.chatMemory.get(this.chatSessionId))
                .chatOptions(DefaultToolCallingChatOptions.builder()
                        .internalToolExecutionEnabled(false)
                        .toolCallbacks(activeTools.toArray(new ToolCallback[0]))
                        .build())
                .build();

        Timer.Sample executeSample = agentMetrics.startSample();
        ToolExecutionResult toolExecutionResult;
        sendStatus(SseMessage.Type.AI_EXECUTING, plan.statusText(), false);
        try {
            toolExecutionResult = toolCallingManager.executeToolCalls(prompt, this.lastChatResponse);
        } finally {
            agentMetrics.stop(executeSample, "agent.tool.execute");
        }

        List<Message> processedConversationHistory = new ArrayList<>(toolExecutionResult.conversationHistory());
        ToolResponseMessage rawToolResponseMessage = (ToolResponseMessage) processedConversationHistory
                .get(processedConversationHistory.size() - 1);

        ToolResponseProcessingResult processingResult = toolResultProcessor.process(
                rawToolResponseMessage,
                this.agentChatOptions,
                this.agentModel
        );

        processedConversationHistory.set(
                processedConversationHistory.size() - 1,
                processingResult.getProcessedMessage()
        );

        // chatMemory is now scratchpad memory for the current run only.
        // Tool results are folded back into runtimeState for cross-turn continuity.
        this.chatMemory.clear(this.chatSessionId);
        this.chatMemory.add(this.chatSessionId, processedConversationHistory);

        String summary = processingResult.getResponses()
                .stream()
                .map(resp -> resp.getRawResponse().name()
                        + " raw=" + resp.getRawContentLength()
                        + " processed=" + resp.getProcessedContentLength())
                .collect(Collectors.joining("\n"));
        log.info("Tool response summary\n{}", summary);

        saveToolMessages(processingResult);
        refreshPendingMessages();
        workflow.onToolResponses(processingResult);
        conversationDigestReducer.onWorkspaceUpdated(runtimeState);
        persistRuntimeState();

        if (rawToolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().equals("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("Workflow terminated by terminate tool");
        }
    }

    private void step(WorkflowStepPlan plan) {
        sendStatus(SseMessage.Type.AI_PLANNING, plan.statusText(), false);
        sendStatus(SseMessage.Type.AI_THINKING, plan.statusText(), false);
        if (emitDeterministicDiagnosisReportIfNeeded(plan)) {
            if (workflow.isFinished()) {
                agentState = AgentState.FINISHED;
            }
            return;
        }
        if (think(plan)) {
            execute(plan);
        } else if (workflow.isFinished()) {
            agentState = AgentState.FINISHED;
        }
    }

    public void run() {
        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent is not idle");
        }

        try {
            // The outer loop is workflow-driven: each round asks the state machine
            // for the next node plan, then runs think/execute within that node.
            for (int i = 0; i < MAX_STEPS && agentState != AgentState.FINISHED && !workflow.isFinished(); i++) {
                WorkflowStepPlan plan = workflow.nextPlan();
                if (plan == null || "DONE".equals(plan.state())) {
                    agentState = AgentState.FINISHED;
                    break;
                }
                step(plan);
            }
            agentState = AgentState.FINISHED;
            sendStatus(SseMessage.Type.AI_DONE, "Current diagnosis turn finished", true);
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            sendStatus(SseMessage.Type.AI_DONE, "Diagnosis turn finished with error", true);
            log.error("Error running agent", e);
            throw new RuntimeException("Error running agent", e);
        }
    }

    @Override
    public String toString() {
        return "JChatMind{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", agentId='" + agentId + '\''
                + ", systemPrompt='" + systemPrompt + '\''
                + '}';
    }

    private int estimateContextChars(List<Message> messages) {
        int totalChars = 0;
        for (Message message : messages) {
            if (message == null || message.getText() == null) {
                continue;
            }
            totalChars += message.getText().length();
        }
        return totalChars;
    }

    private String resolveResponseModel(ChatResponse chatResponse) {
        ChatResponseMetadata metadata = chatResponse != null ? chatResponse.getMetadata() : null;
        if (metadata != null && StringUtils.hasText(metadata.getModel())) {
            return metadata.getModel();
        }
        return this.agentModel;
    }

    private Usage resolveUsage(ChatResponse chatResponse) {
        ChatResponseMetadata metadata = chatResponse != null ? chatResponse.getMetadata() : null;
        return metadata != null ? metadata.getUsage() : null;
    }

    private boolean emitDeterministicDiagnosisReportIfNeeded(WorkflowStepPlan plan) {
        if (plan == null
                || !"GENERATE_DIAGNOSIS".equals(plan.state())
                || runtimeState == null
                || runtimeState.getDiagnosisWorkspace() == null) {
            return false;
        }

        String reportContent = DiagnosisReportComposer.compose(runtimeState);
        AssistantMessage assistantMessage = AssistantMessage.builder().content(reportContent).build();
        ChatMessageDTO savedMessage = saveAssistantMessage(assistantMessage);
        conversationDigestReducer.onAssistantMessage(
                runtimeState,
                savedMessage.getId(),
                reportContent,
                false,
                true
        );
        appendRecentRawMessage(ChatMessageDTO.RoleType.ASSISTANT, savedMessage.getId(), reportContent);
        persistRuntimeState();
        refreshPendingMessages();
        workflow.onAssistantResponse(assistantMessage);
        log.info("Generated deterministic diagnosis report for chatSessionId={}", chatSessionId);
        return true;
    }
}
