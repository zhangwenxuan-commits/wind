package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.model.dto.AgentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultToolResultProcessorTest {

    @Test
    void shouldCompactLongToolOutputWhenCompressionEnabled() {
        DefaultToolResultProcessor processor = new DefaultToolResultProcessor(
                new ChatClientRegistry(Map.of()),
                new ToolSummaryCache(new AgentMetrics(new SimpleMeterRegistry())),
                new AgentMetrics(new SimpleMeterRegistry())
        );

        AgentDTO.ChatOptions chatOptions = AgentDTO.ChatOptions.builder()
                .messageLength(10)
                .contextCompression(AgentDTO.ContextCompressionOptions.builder()
                        .enabled(true)
                        .minCharsToCompress(10)
                        .maxSummaryChars(80)
                        .maxRawPreviewChars(20)
                        .build())
                .runtimeCache(AgentDTO.RuntimeCacheOptions.builder()
                        .toolSummaryEnabled(true)
                        .toolSummaryTtlSeconds(60)
                        .build())
                .build();

        ToolResponseMessage rawMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-1",
                        "databaseQuery",
                        "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau"
                )))
                .build();

        ToolResponseProcessingResult result = processor.process(rawMessage, chatOptions, "missing-model");
        ToolResponseProcessingResult.ProcessedToolResponse processedResponse = result.getResponses().get(0);

        assertTrue(processedResponse.isCompressed());
        assertNotNull(processedResponse.getProcessedResponse());
        assertTrue(processedResponse.getProcessedContentLength() <= 80);
        assertTrue(result.getProcessedMessage().getResponses().get(0).responseData().contains("[tool=databaseQuery]"));
    }

    @Test
    void shouldKeepShortToolOutputUntouched() {
        DefaultToolResultProcessor processor = new DefaultToolResultProcessor(
                new ChatClientRegistry(Map.of()),
                new ToolSummaryCache(new AgentMetrics(new SimpleMeterRegistry())),
                new AgentMetrics(new SimpleMeterRegistry())
        );

        AgentDTO.ChatOptions chatOptions = AgentDTO.ChatOptions.builder()
                .contextCompression(AgentDTO.ContextCompressionOptions.builder()
                        .enabled(true)
                        .minCharsToCompress(100)
                        .maxSummaryChars(60)
                        .maxRawPreviewChars(20)
                        .build())
                .build();

        ToolResponseMessage rawMessage = ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(
                        "call-2",
                        "KnowledgeTool",
                        "short result"
                )))
                .build();

        ToolResponseProcessingResult result = processor.process(rawMessage, chatOptions, "missing-model");

        assertFalse(result.getResponses().get(0).isCompressed());
        assertTrue(result.getProcessedMessage().getResponses().get(0).responseData().equals("short result"));
    }
}
