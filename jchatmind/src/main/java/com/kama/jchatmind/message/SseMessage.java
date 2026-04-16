package com.kama.jchatmind.message;

import com.kama.jchatmind.model.vo.ChatMessageVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class SseMessage {
    /**
     * SSE 业务消息模型。
     * <p>
     * 控制器返回的是 {@code SseEmitter} 流；前端真正每次收到的 message 事件数据，会被序列化为当前对象。
     * </p>
     */
    private Type type;
    private Payload payload;
    private Metadata metadata;

    @Data
    @AllArgsConstructor
    @Builder
    public static class Payload {
        private ChatMessageVO message;
        private String statusText;
        private Boolean done;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class Metadata {
        private String chatMessageId;
    }

    /**
     * 前后端约定的 SSE 消息类型。
     * <p>
     * 其中 {@code AI_GENERATED_CONTENT} 表示真正的聊天内容，
     * 其余类型更多用于展示 Agent 当前所处阶段，如规划中、思考中、执行中、结束。
     * </p>
     */
    public enum Type {
        AI_GENERATED_CONTENT,
        AI_PLANNING,
        AI_THINKING,
        AI_EXECUTING,
        AI_DONE,
    }
}
