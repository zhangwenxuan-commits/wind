package com.kama.jchatmind.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void stop(Timer.Sample sample, String name, String... tags) {
        sample.stop(Timer.builder(name)
                .tags(tags)
                .register(meterRegistry));
    }

    public void increment(String name, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    public void record(String name, double value, String... tags) {
        DistributionSummary.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(value);
    }

    public void recordTokenUsage(String stage, String model, Usage usage) {
        if (usage == null) {
            return;
        }

        String safeStage = StringUtils.hasText(stage) ? stage : "unknown";
        String safeModel = StringUtils.hasText(model) ? model : "unknown";

        if (usage.getPromptTokens() != null) {
            record("agent.llm.tokens", usage.getPromptTokens(), "stage", safeStage, "type", "prompt", "model", safeModel);
        }
        if (usage.getCompletionTokens() != null) {
            record("agent.llm.tokens", usage.getCompletionTokens(), "stage", safeStage, "type", "completion", "model", safeModel);
        }
        if (usage.getTotalTokens() != null) {
            record("agent.llm.tokens", usage.getTotalTokens(), "stage", safeStage, "type", "total", "model", safeModel);
        }
    }
}
