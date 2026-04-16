package com.kama.jchatmind.event.listener;

import com.kama.jchatmind.agent.JChatMind;
import com.kama.jchatmind.agent.JChatMindFactory;
import com.kama.jchatmind.event.ChatEvent;
import com.kama.jchatmind.metrics.AgentMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class ChatEventListener {

    private final JChatMindFactory jChatMindFactory;
    private final AgentMetrics agentMetrics;

    @Async
    @EventListener
    public void handle(ChatEvent event) {
        Timer.Sample sample = agentMetrics.startSample();
        try {
            JChatMind jChatMind = jChatMindFactory.create(event.getAgentId(), event.getSessionId());
            jChatMind.run();
            agentMetrics.increment("agent.turn.completed", "result", "success");
        } catch (Exception e) {
            agentMetrics.increment("agent.turn.completed", "result", "error");
            log.error(
                    "Failed to handle ChatEvent, agentId={}, sessionId={}, userInput={}",
                    event.getAgentId(),
                    event.getSessionId(),
                    event.getUserInput(),
                    e
            );
            throw e;
        } finally {
            agentMetrics.stop(sample, "agent.turn.total");
        }
    }
}
