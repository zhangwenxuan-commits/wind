package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.support.cache.TtlCache;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class SessionMemoryCache {

    private final TtlCache<SessionMemoryKey, List<Message>> cache = new TtlCache<>();
    private final AgentMetrics agentMetrics;

    public SessionMemoryCache(AgentMetrics agentMetrics) {
        this.agentMetrics = agentMetrics;
    }

    public List<Message> getOrLoad(String sessionId, int maxMessages, int ttlSeconds, Supplier<List<Message>> loader) {
        SessionMemoryKey key = new SessionMemoryKey(sessionId, maxMessages);
        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl == null) {
            agentMetrics.increment("agent.cache.requests", "cache", "session-memory", "result", "disabled");
            return sanitize(loader.get(), maxMessages);
        }

        List<Message> cached = cache.get(key);
        if (cached != null) {
            agentMetrics.increment("agent.cache.requests", "cache", "session-memory", "result", "hit");
            return new ArrayList<>(cached);
        }

        agentMetrics.increment("agent.cache.requests", "cache", "session-memory", "result", "miss");
        List<Message> loaded = sanitize(loader.get(), maxMessages);
        cache.put(key, loaded, ttl);
        return new ArrayList<>(loaded);
    }

    public void put(String sessionId, int maxMessages, int ttlSeconds, List<Message> messages) {
        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl == null) {
            return;
        }

        cache.put(
                new SessionMemoryKey(sessionId, maxMessages),
                sanitize(messages, maxMessages),
                ttl
        );
    }

    public void append(String sessionId, Message message) {
        if (message == null || message instanceof SystemMessage) {
            return;
        }

        cache.forEachValid((key, snapshot) -> {
            if (!key.sessionId().equals(sessionId)) {
                return;
            }

            List<Message> updated = new ArrayList<>(snapshot.value());
            updated.add(message);
            cache.put(key, sanitize(updated, key.maxMessages()), snapshot.ttl());
        });
    }

    public void invalidate(String sessionId) {
        cache.invalidateIf(key -> key.sessionId().equals(sessionId));
    }

    private List<Message> sanitize(List<Message> messages, int maxMessages) {
        List<Message> sanitized = new ArrayList<>();
        for (Message message : messages) {
            if (message == null || message instanceof SystemMessage) {
                continue;
            }
            sanitized.add(message);
        }

        if (sanitized.size() <= maxMessages) {
            return sanitized;
        }

        return new ArrayList<>(sanitized.subList(sanitized.size() - maxMessages, sanitized.size()));
    }

    private Duration normalizeTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    private record SessionMemoryKey(String sessionId, int maxMessages) {
    }
}
