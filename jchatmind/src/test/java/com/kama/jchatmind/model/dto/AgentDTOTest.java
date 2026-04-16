package com.kama.jchatmind.model.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDTOTest {

    @Test
    void shouldExposeRuntimeDefaults() {
        AgentDTO.ChatOptions chatOptions = AgentDTO.ChatOptions.defaultOptions();

        assertEquals(10, chatOptions.resolveMessageLength());
        assertFalse(chatOptions.resolveContextCompression().isEnabled());
        assertTrue(chatOptions.resolveRuntimeCache().isSessionMemoryEnabled());
        assertTrue(chatOptions.resolveRuntimeCache().isToolSummaryEnabled());
    }
}
