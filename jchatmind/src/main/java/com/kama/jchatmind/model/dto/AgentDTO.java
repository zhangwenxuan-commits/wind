package com.kama.jchatmind.model.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AgentDTO {
    private String id;

    private String name;

    private String description;

    private String systemPrompt;

    private ModelType model;

    private List<String> allowedTools;

    private List<String> allowedKbs;

    private ChatOptions chatOptions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public enum ModelType {
        DEEPSEEK_CHAT("deepseek-chat"),
        GLM_4_6("glm-4.6");

        @JsonValue
        private final String modelName;

        public static ModelType fromModelName(String modelName) {
            for (ModelType type : ModelType.values()) {
                if (type.modelName.equals(modelName)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown model type: " + modelName);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChatOptions {
        private Double temperature;
        private Double topP;
        private Integer messageLength;
        private ContextCompressionOptions contextCompression;
        private RuntimeCacheOptions runtimeCache;

        private static final Double DEFAULT_TEMPERATURE = 0.7;
        private static final Double DEFAULT_TOP_P = 1.0;
        private static final Integer DEFAULT_MESSAGE_LENGTH = 10;

        public static ChatOptions defaultOptions() {
            return ChatOptions.builder()
                    .temperature(DEFAULT_TEMPERATURE)
                    .topP(DEFAULT_TOP_P)
                    .messageLength(DEFAULT_MESSAGE_LENGTH)
                    .contextCompression(ContextCompressionOptions.defaultOptions())
                    .runtimeCache(RuntimeCacheOptions.defaultOptions())
                    .build();
        }

        public int resolveMessageLength() {
            return messageLength != null ? messageLength : DEFAULT_MESSAGE_LENGTH;
        }

        public double resolveTemperature() {
            return temperature != null ? temperature : DEFAULT_TEMPERATURE;
        }

        public double resolveTopP() {
            return topP != null ? topP : DEFAULT_TOP_P;
        }

        public ContextCompressionOptions resolveContextCompression() {
            return contextCompression != null
                    ? contextCompression.withDefaults()
                    : ContextCompressionOptions.defaultOptions();
        }

        public RuntimeCacheOptions resolveRuntimeCache() {
            return runtimeCache != null
                    ? runtimeCache.withDefaults()
                    : RuntimeCacheOptions.defaultOptions();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextCompressionOptions {
        private Boolean enabled;
        private String model;
        private Integer minCharsToCompress;
        private Integer maxSummaryChars;
        private Integer maxRawPreviewChars;

        private static final Boolean DEFAULT_ENABLED = Boolean.FALSE;
        private static final Integer DEFAULT_MIN_CHARS_TO_COMPRESS = 1200;
        private static final Integer DEFAULT_MAX_SUMMARY_CHARS = 600;
        private static final Integer DEFAULT_MAX_RAW_PREVIEW_CHARS = 160;

        public static ContextCompressionOptions defaultOptions() {
            return ContextCompressionOptions.builder()
                    .enabled(DEFAULT_ENABLED)
                    .model(null)
                    .minCharsToCompress(DEFAULT_MIN_CHARS_TO_COMPRESS)
                    .maxSummaryChars(DEFAULT_MAX_SUMMARY_CHARS)
                    .maxRawPreviewChars(DEFAULT_MAX_RAW_PREVIEW_CHARS)
                    .build();
        }

        public ContextCompressionOptions withDefaults() {
            return ContextCompressionOptions.builder()
                    .enabled(enabled != null ? enabled : DEFAULT_ENABLED)
                    .model(model)
                    .minCharsToCompress(minCharsToCompress != null
                            ? minCharsToCompress
                            : DEFAULT_MIN_CHARS_TO_COMPRESS)
                    .maxSummaryChars(maxSummaryChars != null
                            ? maxSummaryChars
                            : DEFAULT_MAX_SUMMARY_CHARS)
                    .maxRawPreviewChars(maxRawPreviewChars != null
                            ? maxRawPreviewChars
                            : DEFAULT_MAX_RAW_PREVIEW_CHARS)
                    .build();
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RuntimeCacheOptions {
        private Boolean sessionMemoryEnabled;
        private Integer sessionMemoryTtlSeconds;
        private Boolean toolSummaryEnabled;
        private Integer toolSummaryTtlSeconds;

        private static final Boolean DEFAULT_SESSION_MEMORY_ENABLED = Boolean.TRUE;
        private static final Integer DEFAULT_SESSION_MEMORY_TTL_SECONDS = 300;
        private static final Boolean DEFAULT_TOOL_SUMMARY_ENABLED = Boolean.TRUE;
        private static final Integer DEFAULT_TOOL_SUMMARY_TTL_SECONDS = 3600;

        public static RuntimeCacheOptions defaultOptions() {
            return RuntimeCacheOptions.builder()
                    .sessionMemoryEnabled(DEFAULT_SESSION_MEMORY_ENABLED)
                    .sessionMemoryTtlSeconds(DEFAULT_SESSION_MEMORY_TTL_SECONDS)
                    .toolSummaryEnabled(DEFAULT_TOOL_SUMMARY_ENABLED)
                    .toolSummaryTtlSeconds(DEFAULT_TOOL_SUMMARY_TTL_SECONDS)
                    .build();
        }

        public RuntimeCacheOptions withDefaults() {
            return RuntimeCacheOptions.builder()
                    .sessionMemoryEnabled(sessionMemoryEnabled != null
                            ? sessionMemoryEnabled
                            : DEFAULT_SESSION_MEMORY_ENABLED)
                    .sessionMemoryTtlSeconds(sessionMemoryTtlSeconds != null
                            ? sessionMemoryTtlSeconds
                            : DEFAULT_SESSION_MEMORY_TTL_SECONDS)
                    .toolSummaryEnabled(toolSummaryEnabled != null
                            ? toolSummaryEnabled
                            : DEFAULT_TOOL_SUMMARY_ENABLED)
                    .toolSummaryTtlSeconds(toolSummaryTtlSeconds != null
                            ? toolSummaryTtlSeconds
                            : DEFAULT_TOOL_SUMMARY_TTL_SECONDS)
                    .build();
        }

        public boolean isSessionMemoryEnabled() {
            return Boolean.TRUE.equals(sessionMemoryEnabled);
        }

        public boolean isToolSummaryEnabled() {
            return Boolean.TRUE.equals(toolSummaryEnabled);
        }
    }
}
