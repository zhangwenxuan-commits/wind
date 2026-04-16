package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.config.ChatClientRegistry;
import com.kama.jchatmind.metrics.AgentMetrics;
import com.kama.jchatmind.model.dto.AgentDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultToolResultProcessor implements ToolResultProcessor {

    private static final String PROMPT_VERSION = "tool-context-v1";

    private final ChatClientRegistry chatClientRegistry;
    private final ToolSummaryCache toolSummaryCache;
    private final AgentMetrics agentMetrics;

    @Override
    public ToolResponseProcessingResult process(
            ToolResponseMessage rawToolResponseMessage,
            AgentDTO.ChatOptions chatOptions,
            String primaryModel
    ) {
        Timer.Sample sample = agentMetrics.startSample();
        AgentDTO.ContextCompressionOptions compressionOptions = chatOptions.resolveContextCompression();
        AgentDTO.RuntimeCacheOptions cacheOptions = chatOptions.resolveRuntimeCache();

        List<ToolResponseProcessingResult.ProcessedToolResponse> processedResponses = new ArrayList<>();
        List<ToolResponseMessage.ToolResponse> responsesForMemory = new ArrayList<>();

        try {
            for (ToolResponseMessage.ToolResponse rawResponse : rawToolResponseMessage.getResponses()) {
                String rawContent = rawResponse.responseData() != null ? rawResponse.responseData() : "";
                String effectiveModel = resolveCompressionModel(compressionOptions, primaryModel);

                String processedContent = rawContent;
                boolean compressed = false;

                if (compressionOptions.isEnabled() && rawContent.length() >= compressionOptions.getMinCharsToCompress()) {
                    processedContent = summarize(
                            rawResponse.name(),
                            rawContent,
                            effectiveModel,
                            compressionOptions,
                            cacheOptions
                    );
                    compressed = !rawContent.equals(processedContent);
                }

                ToolResponseMessage.ToolResponse processedResponse = compressed
                        ? new ToolResponseMessage.ToolResponse(rawResponse.id(), rawResponse.name(), processedContent)
                        : rawResponse;

                responsesForMemory.add(processedResponse);
                processedResponses.add(ToolResponseProcessingResult.ProcessedToolResponse.builder()
                        .rawResponse(rawResponse)
                        .processedResponse(compressed ? processedResponse : null)
                        .compressed(compressed)
                        .compressionModel(compressed ? effectiveModel : null)
                        .rawContentLength(rawContent.length())
                        .processedContentLength(processedContent.length())
                        .build());

                agentMetrics.record("agent.tool.payload.chars", rawContent.length(), "stage", "raw");
                agentMetrics.record("agent.tool.payload.chars", processedContent.length(), "stage", "processed");
            }

            return ToolResponseProcessingResult.builder()
                    .processedMessage(ToolResponseMessage.builder()
                            .responses(responsesForMemory)
                            .build())
                    .responses(processedResponses)
                    .build();
        } finally {
            agentMetrics.stop(sample, "agent.tool.process");
        }
    }

    private String summarize(
            String toolName,
            String rawContent,
            String model,
            AgentDTO.ContextCompressionOptions compressionOptions,
            AgentDTO.RuntimeCacheOptions cacheOptions
    ) {
        ToolSummaryCache.ToolSummaryCacheKey key = ToolSummaryCache.ToolSummaryCacheKey.of(
                PROMPT_VERSION,
                model,
                toolName,
                rawContent,
                compressionOptions.getMaxSummaryChars(),
                compressionOptions.getMaxRawPreviewChars()
        );

        if (cacheOptions.isToolSummaryEnabled()) {
            return toolSummaryCache.getOrLoad(
                    key,
                    cacheOptions.getToolSummaryTtlSeconds(),
                    () -> summarizeWithFallback(toolName, rawContent, model, compressionOptions)
            );
        }

        return summarizeWithFallback(toolName, rawContent, model, compressionOptions);
    }

    private String summarizeWithFallback(
            String toolName,
            String rawContent,
            String model,
            AgentDTO.ContextCompressionOptions compressionOptions
    ) {
        ChatClient chatClient = StringUtils.hasText(model) ? chatClientRegistry.get(model) : null;
        if (chatClient == null) {
            return deterministicCompact(toolName, rawContent, compressionOptions);
        }

        try {
            Timer.Sample sample = agentMetrics.startSample();
            ChatResponse response;
            try {
                response = chatClient.prompt()
                        .system(buildSystemPrompt(compressionOptions.getMaxSummaryChars()))
                        .user(buildUserPrompt(toolName, rawContent))
                        .call()
                        .chatClientResponse()
                        .chatResponse();
            } finally {
                agentMetrics.stop(sample, "agent.llm.compression");
            }

            agentMetrics.recordTokenUsage("compression", resolveResponseModel(response, model), resolveUsage(response));
            String content = extractText(response);

            if (!StringUtils.hasText(content)) {
                return deterministicCompact(toolName, rawContent, compressionOptions);
            }

            return trimToLength(content.trim(), compressionOptions.getMaxSummaryChars());
        } catch (Exception e) {
            log.warn("Tool result compression failed, fallback to deterministic compaction. tool={}, model={}",
                    toolName,
                    model,
                    e
            );
            return deterministicCompact(toolName, rawContent, compressionOptions);
        }
    }

    private String resolveCompressionModel(AgentDTO.ContextCompressionOptions compressionOptions, String primaryModel) {
        if (StringUtils.hasText(compressionOptions.getModel())) {
            return compressionOptions.getModel();
        }
        return primaryModel;
    }

    private String buildSystemPrompt(int maxSummaryChars) {
        return """
                You compress tool outputs for another LLM.
                Keep only facts needed for subsequent reasoning.
                Preserve numbers, identifiers, dates, statuses, errors, and constraints.
                Do not answer the user directly.
                Do not invent missing information.
                Return plain text within %d characters.
                """.formatted(maxSummaryChars);
    }

    private String buildUserPrompt(String toolName, String rawContent) {
        return """
                Tool name:
                %s

                Raw tool output:
                %s
                """.formatted(toolName, rawContent);
    }

    private String deterministicCompact(
            String toolName,
            String rawContent,
            AgentDTO.ContextCompressionOptions compressionOptions
    ) {
        String normalized = normalizeWhitespace(rawContent);
        int maxSummaryChars = compressionOptions.getMaxSummaryChars();
        if (normalized.length() <= maxSummaryChars) {
            return normalized;
        }

        int previewChars = Math.min(compressionOptions.getMaxRawPreviewChars(), Math.max(40, maxSummaryChars / 4));
        String head = normalized.substring(0, Math.min(previewChars, normalized.length()));
        String tail = normalized.substring(Math.max(0, normalized.length() - previewChars));
        String compact = """
                [tool=%s] raw_length=%d
                head: %s
                ...
                tail: %s
                """.formatted(toolName, normalized.length(), head, tail);
        return trimToLength(compact, maxSummaryChars);
    }

    private String normalizeWhitespace(String rawContent) {
        return rawContent == null ? "" : rawContent.replaceAll("\\s+", " ").trim();
    }

    private String trimToLength(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        if (maxLength <= 3) {
            return content.substring(0, maxLength);
        }
        return content.substring(0, maxLength - 3) + "...";
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private String resolveResponseModel(ChatResponse response, String fallbackModel) {
        ChatResponseMetadata metadata = response != null ? response.getMetadata() : null;
        if (metadata != null && StringUtils.hasText(metadata.getModel())) {
            return metadata.getModel();
        }
        return fallbackModel;
    }

    private Usage resolveUsage(ChatResponse response) {
        ChatResponseMetadata metadata = response != null ? response.getMetadata() : null;
        return metadata != null ? metadata.getUsage() : null;
    }
}
