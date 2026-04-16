package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.model.dto.AgentDTO;
import org.springframework.ai.chat.messages.ToolResponseMessage;

public interface ToolResultProcessor {

    ToolResponseProcessingResult process(
            ToolResponseMessage rawToolResponseMessage,
            AgentDTO.ChatOptions chatOptions,
            String primaryModel
    );
}
