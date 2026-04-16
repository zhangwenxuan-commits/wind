package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.metrics.AgentMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionMemoryCacheTest {

    @Test
    void shouldCacheLoadedMessagesAndStripSystemPrompt() {
        SessionMemoryCache cache = new SessionMemoryCache(new AgentMetrics(new SimpleMeterRegistry()));
        AtomicInteger loadCount = new AtomicInteger();

        List<Message> first = cache.getOrLoad(
                "session-1",
                3,
                60,
                () -> {
                    loadCount.incrementAndGet();
                    return List.of(
                            new SystemMessage("system"),
                            new UserMessage("u1"),
                            AssistantMessage.builder().content("a1").build()
                    );
                }
        );

        List<Message> second = cache.getOrLoad(
                "session-1",
                3,
                60,
                List::of
        );

        assertEquals(1, loadCount.get());
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals("u1", ((UserMessage) second.get(0)).getText());
    }

    @Test
    void shouldAppendAndTrimPerWindowSize() {
        SessionMemoryCache cache = new SessionMemoryCache(new AgentMetrics(new SimpleMeterRegistry()));

        cache.put(
                "session-2",
                2,
                60,
                List.of(new UserMessage("u1"), AssistantMessage.builder().content("a1").build())
        );
        cache.put(
                "session-2",
                3,
                60,
                List.of(new UserMessage("u1"), AssistantMessage.builder().content("a1").build())
        );

        cache.append("session-2", new UserMessage("u2"));

        List<Message> window2 = cache.getOrLoad("session-2", 2, 60, List::of);
        List<Message> window3 = cache.getOrLoad("session-2", 3, 60, List::of);

        assertEquals(2, window2.size());
        assertEquals("a1", ((AssistantMessage) window2.get(0)).getText());
        assertEquals("u2", ((UserMessage) window2.get(1)).getText());

        assertEquals(3, window3.size());
        assertEquals("u1", ((UserMessage) window3.get(0)).getText());
        assertEquals("u2", ((UserMessage) window3.get(2)).getText());
    }
}
