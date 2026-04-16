package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.model.dto.ChatMessageDTO;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class MemoryMessageMapper {

    public Message toMessage(ChatMessageDTO chatMessageDTO) {
        return switch (chatMessageDTO.getRole()) {
            case SYSTEM -> {
                if (!StringUtils.hasLength(chatMessageDTO.getContent())) {
                    yield null;
                }
                yield new SystemMessage(chatMessageDTO.getContent());
            }
            case USER -> {
                if (!StringUtils.hasLength(chatMessageDTO.getContent())) {
                    yield null;
                }
                yield new UserMessage(chatMessageDTO.getContent());
            }
            case ASSISTANT -> AssistantMessage.builder()
                    .content(chatMessageDTO.getContent())
                    .toolCalls(chatMessageDTO.getMetadata() != null
                            ? chatMessageDTO.getMetadata().getToolCalls()
                            : null)
                    .build();
            case TOOL -> {
                ChatMessageDTO.MetaData metadata = chatMessageDTO.getMetadata();
                if (metadata == null) {
                    yield null;
                }
                ToolResponseMessage.ToolResponse effectiveResponse = metadata.getProcessedToolResponse() != null
                        ? metadata.getProcessedToolResponse()
                        : metadata.getToolResponse();
                if (effectiveResponse == null) {
                    yield null;
                }
                yield ToolResponseMessage.builder()
                        .responses(List.of(effectiveResponse))
                        .build();
            }
        };
    }
}
