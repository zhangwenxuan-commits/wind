package com.kama.jchatmind.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kama.jchatmind.mapper.ChatSessionMapper;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.model.entity.ChatSession;
import com.kama.jchatmind.support.cache.TtlCache;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class SessionRuntimeStateStore {

    private static final String RUNTIME_STATE_FIELD = "runtimeState";

    private final ChatSessionMapper chatSessionMapper;
    private final ObjectMapper objectMapper;
    private final AgentMetrics agentMetrics;
    private final TtlCache<String, SessionRuntimeState> cache = new TtlCache<>();

    public SessionRuntimeStateStore(
            ChatSessionMapper chatSessionMapper,
            ObjectMapper objectMapper,
            AgentMetrics agentMetrics
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.objectMapper = objectMapper;
        this.agentMetrics = agentMetrics;
    }

    public SessionRuntimeState loadOrCreate(
            String sessionId,
            String agentId,
            int ttlSeconds,
            Supplier<SessionRuntimeState> initializer
    ) {
        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl != null) {
            SessionRuntimeState cached = cache.get(sessionId);
            if (cached != null) {
                agentMetrics.increment("agent.cache.requests", "cache", "session-runtime", "result", "hit");
                ensureDefaults(cached, sessionId, agentId);
                return cached;
            }
            agentMetrics.increment("agent.cache.requests", "cache", "session-runtime", "result", "miss");
        } else {
            agentMetrics.increment("agent.cache.requests", "cache", "session-runtime", "result", "disabled");
        }

        SessionRuntimeState state = loadFromSessionMetadata(sessionId);
        if (state == null) {
            state = initializer.get();
        }
        ensureDefaults(state, sessionId, agentId);

        if (ttl != null) {
            cache.put(sessionId, state, ttl);
        }
        return state;
    }

    public void save(SessionRuntimeState runtimeState, int ttlSeconds) {
        if (runtimeState == null || runtimeState.getSessionId() == null) {
            return;
        }

        ChatSession existing = chatSessionMapper.selectById(runtimeState.getSessionId());
        if (existing != null) {
            ObjectNode metadata = parseMetadata(existing.getMetadata());
            metadata.set(RUNTIME_STATE_FIELD, objectMapper.valueToTree(runtimeState));
            ChatSession update = ChatSession.builder()
                    .id(existing.getId())
                    .metadata(metadata.toString())
                    .build();
            chatSessionMapper.updateById(update);
        }

        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl != null) {
            cache.put(runtimeState.getSessionId(), runtimeState, ttl);
        }
    }

    public void invalidate(String sessionId) {
        cache.invalidate(sessionId);
    }

    private SessionRuntimeState loadFromSessionMetadata(String sessionId) {
        ChatSession chatSession = chatSessionMapper.selectById(sessionId);
        if (chatSession == null || chatSession.getMetadata() == null || chatSession.getMetadata().isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(chatSession.getMetadata());
            JsonNode runtimeStateNode = root.get(RUNTIME_STATE_FIELD);
            if (runtimeStateNode == null || runtimeStateNode.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(runtimeStateNode, SessionRuntimeState.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            if (root instanceof ObjectNode objectNode) {
                return objectNode;
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }

    private void ensureDefaults(SessionRuntimeState runtimeState, String sessionId, String agentId) {
        runtimeState.setSessionId(sessionId);
        if (runtimeState.getAgentId() == null) {
            runtimeState.setAgentId(agentId);
        }
        runtimeState.ensureDigest();
        runtimeState.ensureRecentMessages();
    }

    private Duration normalizeTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(ttlSeconds);
    }
}
