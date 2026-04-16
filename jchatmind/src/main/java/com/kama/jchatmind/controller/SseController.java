package com.kama.jchatmind.controller;

import com.kama.jchatmind.service.SseService;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE(Server-Sent Events) 推送入口。
 * <p>
 * 这个控制器只负责为前端建立会话级别的流式连接，本身不执行 AI 推理，也不创建聊天消息。
 * 真正的用途是：当前端已经进入某个 chatSession 后，先连接这里；后端在 Agent 产生结果时，再沿着这条连接把消息推送回前端。
 * </p>
 * <p>
 * 当前项目中的完整链路如下：
 * 1. 前端 {@code AgentChatView} 打开页面后，调用 {@code /sse/connect/{chatSessionId}} 建立 EventSource 长连接。
 * 2. 用户发送消息到 {@code POST /api/chat-messages}。
 * 3. 后端保存用户消息，并发布 {@code ChatEvent}。
 * 4. {@code ChatEventListener} 异步启动 {@code JChatMind}。
 * 5. {@code JChatMind} 在执行过程中保存 AI/工具消息，并调用 {@code SseService.send(chatSessionId, sseMessage)}。
 * 6. {@code SseServiceImpl} 根据 chatSessionId 找到这里建立的连接，把 {@code SseMessage} 持续推送给前端。
 * 7. 前端监听 {@code message} 事件，把消息追加到聊天窗口。
 * </p>
 */
@RestController
@RequestMapping("/sse")
@AllArgsConstructor
public class SseController {

    private final SseService sseService;

    /**
     * 建立指定聊天会话的 SSE 长连接。
     * <p>
     * 请求路径：{@code /sse/connect/{chatSessionId}}
     * </p>
     * <p>
     * 参数说明：
     * {@code chatSessionId} 表示聊天会话 ID。
     * </p>
     * <p>
     * 为什么需要这个参数：
     * 1. 当前系统没有单独设计用户连接 ID，所以直接用 chatSessionId 作为 SSE 连接标识。
     * 2. 后端后续推送时，需要靠这个 ID 把 AI 结果准确路由到当前会话对应的浏览器页面。
     * 3. {@code SseServiceImpl} 内部会把它作为 key 保存到连接池里。
     * </p>
     * <p>
     * 返回值说明：
     * 返回 {@link SseEmitter}，响应类型是 {@code text/event-stream}，表示一个持续打开的流，而不是普通 JSON 响应。
     * 建连成功后，服务端会先发送一条 {@code init} 事件；后续业务消息以 {@code message} 事件推送，
     * 事件数据体是 {@code SseMessage} 的 JSON 字符串，内部包含：
     * 1. {@code type}：消息类型，例如 {@code AI_GENERATED_CONTENT}。
     * 2. {@code payload}：消息正文，例如 AI 生成的 {@code ChatMessageVO} 或状态文本。
     * 3. {@code metadata}：附加信息，例如 {@code chatMessageId}。
     * </p>
     * <p>
     * 注意：
     * 这个接口只负责“接收订阅/建立连接”，不会主动让 AI 开始回答。
     * 真正触发 AI 处理的入口仍然是创建用户消息接口。
     * </p>
     */
    @RequestMapping(value = "/connect/{chatSessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@PathVariable String chatSessionId) {
        return sseService.connect(chatSessionId);
    }
}
