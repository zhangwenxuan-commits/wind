package com.kama.jchatmind.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.agent.runtime.ContextAssembler;
import com.kama.jchatmind.agent.runtime.ConversationDigestReducer;
import com.kama.jchatmind.agent.runtime.MemoryMessageMapper;
import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import com.kama.jchatmind.agent.runtime.SessionRuntimeStateStore;
import com.kama.jchatmind.agent.runtime.SessionMemoryCache;
import com.kama.jchatmind.agent.runtime.ToolResultProcessor;
import com.kama.jchatmind.agent.tools.Tool;
import com.kama.jchatmind.agent.workflow.AgentWorkflow;
import com.kama.jchatmind.agent.workflow.AgentWorkflowFactory;
import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.converter.AgentConverter;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.mapper.AgentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.model.dto.AgentDTO;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.dto.KnowledgeBaseDTO;
import com.kama.jchatmind.model.entity.Agent;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import com.kama.jchatmind.service.SseService;
import com.kama.jchatmind.service.ToolFacadeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JChatMindFactory {

    private static final int RECENT_RAW_SCAN_LIMIT = 12;
    private static final int RECENT_RAW_MESSAGE_LIMIT = 4;

    private static final Logger log = LoggerFactory.getLogger(JChatMindFactory.class);

    private final ChatClientRegistry chatClientRegistry;
    private final SseService sseService;
    private final AgentMapper agentMapper;
    private final AgentConverter agentConverter;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;
    private final ToolFacadeService toolFacadeService;
    private final ChatMessageFacadeService chatMessageFacadeService;
    private final ChatMessageConverter chatMessageConverter;
    private final SessionMemoryCache sessionMemoryCache;
    private final MemoryMessageMapper memoryMessageMapper;
    private final ToolResultProcessor toolResultProcessor;
    private final AgentMetrics agentMetrics;
    private final AgentWorkflowFactory agentWorkflowFactory;
    private final SessionRuntimeStateStore sessionRuntimeStateStore;
    private final ConversationDigestReducer conversationDigestReducer;
    private final ContextAssembler contextAssembler;

    private AgentDTO agentConfig;

    public JChatMindFactory(
            ChatClientRegistry chatClientRegistry,
            SseService sseService,
            AgentMapper agentMapper,
            AgentConverter agentConverter,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeBaseConverter knowledgeBaseConverter,
            ToolFacadeService toolFacadeService,
            ChatMessageFacadeService chatMessageFacadeService,
            ChatMessageConverter chatMessageConverter,
            SessionMemoryCache sessionMemoryCache,
            MemoryMessageMapper memoryMessageMapper,
            ToolResultProcessor toolResultProcessor,
            AgentMetrics agentMetrics,
            AgentWorkflowFactory agentWorkflowFactory,
            SessionRuntimeStateStore sessionRuntimeStateStore,
            ConversationDigestReducer conversationDigestReducer,
            ContextAssembler contextAssembler
    ) {
        this.chatClientRegistry = chatClientRegistry;
        this.sseService = sseService;
        this.agentMapper = agentMapper;
        this.agentConverter = agentConverter;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeBaseConverter = knowledgeBaseConverter;
        this.toolFacadeService = toolFacadeService;
        this.chatMessageFacadeService = chatMessageFacadeService;
        this.chatMessageConverter = chatMessageConverter;
        this.sessionMemoryCache = sessionMemoryCache;
        this.memoryMessageMapper = memoryMessageMapper;
        this.toolResultProcessor = toolResultProcessor;
        this.agentMetrics = agentMetrics;
        this.agentWorkflowFactory = agentWorkflowFactory;
        this.sessionRuntimeStateStore = sessionRuntimeStateStore;
        this.conversationDigestReducer = conversationDigestReducer;
        this.contextAssembler = contextAssembler;
    }

    private Agent loadAgent(String agentId) {
        return agentMapper.selectById(agentId);
    }

    private List<ChatMessageDTO> loadRecentRawMessages(String chatSessionId) {
        Timer.Sample sample = agentMetrics.startSample();
        try {
            List<ChatMessageDTO> chatMessages = chatMessageFacadeService
                    .getChatMessagesBySessionIdRecently(chatSessionId, RECENT_RAW_SCAN_LIMIT);

            List<ChatMessageDTO> rawMessages = chatMessages.stream()
                    .filter(message -> message.getRole() == ChatMessageDTO.RoleType.USER
                            || message.getRole() == ChatMessageDTO.RoleType.ASSISTANT)
                    .filter(message -> message.getContent() != null && !message.getContent().isBlank())
                    .collect(Collectors.toList());

            if (rawMessages.size() <= RECENT_RAW_MESSAGE_LIMIT) {
                return rawMessages;
            }
            return new ArrayList<>(rawMessages.subList(rawMessages.size() - RECENT_RAW_MESSAGE_LIMIT, rawMessages.size()));
        } finally {
            agentMetrics.stop(sample, "agent.recent-raw.load");
        }
    }

    private SessionRuntimeState loadRuntimeState(
            Agent agent,
            AgentDTO agentConfig,
            String chatSessionId,
            List<ChatMessageDTO> recentRawMessages
    ) {
        AgentDTO.RuntimeCacheOptions runtimeCacheOptions = agentConfig.getChatOptions().resolveRuntimeCache();
        SessionRuntimeState runtimeState = sessionRuntimeStateStore.loadOrCreate(
                chatSessionId,
                agent.getId(),
                runtimeCacheOptions.getSessionMemoryTtlSeconds(),
                () -> SessionRuntimeState.create(chatSessionId, agent.getId())
        );
        conversationDigestReducer.syncRecentMessages(runtimeState, recentRawMessages);
        sessionRuntimeStateStore.save(runtimeState, runtimeCacheOptions.getSessionMemoryTtlSeconds());
        return runtimeState;
    }

    private AgentDTO toAgentConfig(Agent agent) {
        try {
            agentConfig = agentConverter.toDTO(agent);
            if (agentConfig.getChatOptions() == null) {
                agentConfig.setChatOptions(AgentDTO.ChatOptions.defaultOptions());
            }
            return agentConfig;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("瑙ｆ瀽 Agent 閰嶇疆澶辫触", e);
        }
    }

    private List<KnowledgeBaseDTO> resolveRuntimeKnowledgeBases(AgentDTO agentConfig) {
        List<String> allowedKbIds = agentConfig.getAllowedKbs();
        if (allowedKbIds == null || allowedKbIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectByIdBatch(allowedKbIds);
        if (knowledgeBases.isEmpty()) {
            return Collections.emptyList();
        }

        List<KnowledgeBaseDTO> kbDTOs = new ArrayList<>();
        try {
            for (KnowledgeBase knowledgeBase : knowledgeBases) {
                kbDTOs.add(knowledgeBaseConverter.toDTO(knowledgeBase));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return kbDTOs;
    }

    private List<Tool> resolveRuntimeTools(AgentDTO agentConfig) {
        return DiagnosisRuntimeTools.resolve(
                toolFacadeService.getFixedTools(),
                toolFacadeService.getOptionalTools(),
                agentConfig.getAllowedTools()
        );
    }

    private List<ToolCallback> buildToolCallbacks(List<Tool> runtimeTools) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Tool tool : runtimeTools) {
            Object target = resolveToolTarget(tool);
            ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(target)
                    .build()
                    .getToolCallbacks();
            callbacks.addAll(Arrays.asList(toolCallbacks));
        }
        return callbacks;
    }

    private Object resolveToolTarget(Tool tool) {
        Object target = AopProxyUtils.getSingletonTarget(tool);
        return target != null ? target : tool;
    }

    private JChatMind buildAgentRuntime(
            Agent agent,
            AgentDTO agentConfig,
            SessionRuntimeState runtimeState,
            List<KnowledgeBaseDTO> knowledgeBases,
            List<Tool> runtimeTools,
            List<ToolCallback> toolCallbacks,
            AgentWorkflow workflow,
            String chatSessionId
    ) {
        ChatClient chatClient = chatClientRegistry.get(agent.getModel());
        if (Objects.isNull(chatClient)) {
            throw new IllegalStateException("鏈壘鍒板搴旂殑 ChatClient: " + agent.getModel());
        }

        return new JChatMind(
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getSystemPrompt(),
                agent.getModel(),
                chatClient,
                agentConfig.getChatOptions(),
                contextAssembler.buildSeedMessages(runtimeState),
                toolCallbacks,
                knowledgeBases,
                workflow,
                runtimeState,
                chatSessionId,
                sseService,
                chatMessageFacadeService,
                chatMessageConverter,
                sessionMemoryCache,
                toolResultProcessor,
                agentMetrics,
                sessionRuntimeStateStore,
                conversationDigestReducer,
                contextAssembler
        );
    }

    public JChatMind create(String agentId, String chatSessionId) {
        Timer.Sample sample = agentMetrics.startSample();
        try {
            Agent agent = loadAgent(agentId);
            AgentDTO agentConfig = toAgentConfig(agent);
            List<ChatMessageDTO> recentRawMessages = loadRecentRawMessages(chatSessionId);
            SessionRuntimeState runtimeState = loadRuntimeState(agent, agentConfig, chatSessionId, recentRawMessages);
            List<KnowledgeBaseDTO> knowledgeBases = resolveRuntimeKnowledgeBases(agentConfig);
            List<Tool> runtimeTools = resolveRuntimeTools(agentConfig);
            List<ToolCallback> toolCallbacks = buildToolCallbacks(runtimeTools);
            AgentWorkflow workflow = agentWorkflowFactory.create(
                    agentConfig,
                    runtimeState,
                    knowledgeBases,
                    toolCallbacks
            );

            return buildAgentRuntime(
                    agent,
                    agentConfig,
                    runtimeState,
                    knowledgeBases,
                    runtimeTools,
                    toolCallbacks,
                    workflow,
                    chatSessionId
            );
        } finally {
            agentMetrics.stop(sample, "agent.runtime.create");
        }
    }
}
