package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.support.cache.TtlCache;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

@Component
public class ToolSummaryCache {

    private final TtlCache<ToolSummaryCacheKey, String> cache = new TtlCache<>();
    private final AgentMetrics agentMetrics;

    public ToolSummaryCache(AgentMetrics agentMetrics) {
        this.agentMetrics = agentMetrics;
    }

    public String getOrLoad(ToolSummaryCacheKey key, int ttlSeconds, Supplier<String> loader) {
        if (ttlSeconds <= 0) {
            agentMetrics.increment("agent.cache.requests", "cache", "tool-summary", "result", "disabled");
            return loader.get();
        }

        String cached = cache.get(key);
        if (cached != null) {
            agentMetrics.increment("agent.cache.requests", "cache", "tool-summary", "result", "hit");
            return cached;
        }

        agentMetrics.increment("agent.cache.requests", "cache", "tool-summary", "result", "miss");
        String loaded = loader.get();
        cache.put(key, loaded, Duration.ofSeconds(ttlSeconds));
        return loaded;
    }

    public record ToolSummaryCacheKey(
            String version,
            String model,
            String toolName,
            String rawHash,
            int maxSummaryChars,
            int maxRawPreviewChars
    ) {
        public static ToolSummaryCacheKey of(
                String version,
                String model,
                String toolName,
                String rawPayload,
                int maxSummaryChars,
                int maxRawPreviewChars
        ) {
            return new ToolSummaryCacheKey(
                    version,
                    model,
                    toolName,
                    sha256(rawPayload),
                    maxSummaryChars,
                    maxRawPreviewChars
            );
        }

        private static String sha256(String rawPayload) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 unavailable", e);
            }
        }
    }
}
