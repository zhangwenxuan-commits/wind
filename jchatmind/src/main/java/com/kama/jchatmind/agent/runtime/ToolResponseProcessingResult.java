package com.kama.jchatmind.agent.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ToolResponseProcessingResult {
    private final ToolResponseMessage processedMessage;
    private final List<ProcessedToolResponse> responses;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProcessedToolResponse {
        private final ToolResponseMessage.ToolResponse rawResponse;
        private final ToolResponseMessage.ToolResponse processedResponse;
        private final boolean compressed;
        private final String compressionModel;
        private final int rawContentLength;
        private final int processedContentLength;
    }
}
