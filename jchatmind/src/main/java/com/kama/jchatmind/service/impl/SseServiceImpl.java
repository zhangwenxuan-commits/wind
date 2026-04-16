package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.message.SseMessage;
import com.kama.jchatmind.service.SseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@AllArgsConstructor
public class SseServiceImpl implements SseService {

    /**
     * chatSessionId -> 当前活跃的 SSE 连接。
     * 同一个会话只保留一个最新连接，避免浏览器刷新后旧连接残留。
     */
    private final ConcurrentMap<String, SseEmitter> clients = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public SseEmitter connect(String chatSessionId) {
        // TODO: 当前重连只会重新建立 SSE 通道，不会补发断线期间错过的事件。
        // TODO: 后续需要增加会话级事件补偿和运行状态快照恢复能力。
        // 0L 表示不主动超时，交给浏览器断开/重连，避免长任务期间连接被服务端超时回收。
        SseEmitter emitter = new SseEmitter(0L);
        SseEmitter previousEmitter = clients.put(chatSessionId, emitter);

        if (previousEmitter != null) {
            log.info("Replacing existing SSE client for chatSessionId={}", chatSessionId);
            safeComplete(previousEmitter);
        }

        emitter.onCompletion(() -> removeClient(chatSessionId, emitter, "completed"));
        emitter.onTimeout(() -> removeClient(chatSessionId, emitter, "timeout"));
        emitter.onError(error -> {
            log.warn("SSE connection error for chatSessionId={}: {}", chatSessionId, error.getMessage());
            removeClient(chatSessionId, emitter, "error");
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("init")
                    .data("connected"));
        } catch (IOException e) {
            removeClient(chatSessionId, emitter, "init-send-failed");
            throw new RuntimeException("Failed to initialize SSE connection for chatSessionId: " + chatSessionId, e);
        }

        log.info("SSE client connected for chatSessionId={}", chatSessionId);
        return emitter;
    }

    @Override
    public void send(String chatSessionId, SseMessage message) {
        SseEmitter emitter = clients.get(chatSessionId);
        if (emitter == null) {
            // TODO: 当前无连接时直接跳过推送；后续如需断线补偿，这里应把事件写入可重放队列/存储。
            // SSE 只是推送通道，不应该影响聊天主流程。
            log.warn("Skipping SSE push because no client is connected for chatSessionId={}", chatSessionId);
            return;
        }

        try {
            String sseMessageStr = objectMapper.writeValueAsString(message);
            emitter.send(SseEmitter.event()
                    .name("message")
                    .data(sseMessageStr));
        } catch (IOException e) {
            log.warn("Failed to push SSE message for chatSessionId={}, removing stale client: {}", chatSessionId, e.getMessage());
            removeClient(chatSessionId, emitter, "send-failed");
        }
    }

    private void removeClient(String chatSessionId, SseEmitter emitter, String reason) {
        boolean removed = clients.remove(chatSessionId, emitter);
        if (removed) {
            log.info("SSE client removed for chatSessionId={}, reason={}", chatSessionId, reason);
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("Ignoring error while completing stale SSE emitter: {}", e.getMessage());
        }
    }
}
