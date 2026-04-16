package com.kama.jchatmind.agent.workflow.vibration;

import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.support.cache.TtlCache;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class DiagnosisWorkspaceCache {

    private final TtlCache<String, DiagnosisWorkspace> cache = new TtlCache<>();
    private final AgentMetrics agentMetrics;

    public DiagnosisWorkspaceCache(AgentMetrics agentMetrics) {
        this.agentMetrics = agentMetrics;
    }

    public DiagnosisWorkspace getOrCreate(String sessionId, int ttlSeconds, Supplier<DiagnosisWorkspace> supplier) {
        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl == null) {
            agentMetrics.increment("agent.cache.requests", "cache", "workflow-memory", "result", "disabled");
            return supplier.get();
        }

        DiagnosisWorkspace cached = cache.get(sessionId);
        if (cached != null) {
            agentMetrics.increment("agent.cache.requests", "cache", "workflow-memory", "result", "hit");
            return cached;
        }

        agentMetrics.increment("agent.cache.requests", "cache", "workflow-memory", "result", "miss");
        DiagnosisWorkspace created = supplier.get();
        cache.put(sessionId, created, ttl);
        return created;
    }

    public void put(String sessionId, int ttlSeconds, DiagnosisWorkspace workspace) {
        Duration ttl = normalizeTtl(ttlSeconds);
        if (ttl == null) {
            return;
        }
        cache.put(sessionId, workspace, ttl);
    }

    public void invalidate(String sessionId) {
        cache.invalidate(sessionId);
    }

    private Duration normalizeTtl(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return null;
        }
        return Duration.ofSeconds(ttlSeconds);
    }
}
