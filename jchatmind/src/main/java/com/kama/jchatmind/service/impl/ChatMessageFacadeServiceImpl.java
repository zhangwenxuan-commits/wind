package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.agent.runtime.MemoryMessageMapper;
import com.kama.jchatmind.agent.runtime.SessionMemoryCache;
import com.kama.jchatmind.converter.ChatMessageConverter;
import com.kama.jchatmind.event.ChatEvent;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.ChatMessageMapper;
import com.kama.jchatmind.model.dto.ChatMessageDTO;
import com.kama.jchatmind.model.entity.ChatMessage;
import com.kama.jchatmind.model.request.CreateChatMessageRequest;
import com.kama.jchatmind.model.request.UpdateChatMessageRequest;
import com.kama.jchatmind.model.response.CreateChatMessageResponse;
import com.kama.jchatmind.model.response.GetChatMessagesResponse;
import com.kama.jchatmind.model.vo.ChatMessageVO;
import com.kama.jchatmind.service.ChatMessageFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatMessageFacadeServiceImpl implements ChatMessageFacadeService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatMessageConverter chatMessageConverter;
    private final ApplicationEventPublisher publisher;
    private final SessionMemoryCache sessionMemoryCache;
    private final MemoryMessageMapper memoryMessageMapper;

    @Override
    public GetChatMessagesResponse getChatMessagesBySessionId(String sessionId) {
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionId(sessionId);
        List<ChatMessageVO> result = new ArrayList<>();

        for (ChatMessage chatMessage : chatMessages) {
            try {
                ChatMessageVO vo = chatMessageConverter.toVO(chatMessage);
                result.add(vo);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        return GetChatMessagesResponse.builder()
                .chatMessages(result.toArray(new ChatMessageVO[0]))
                .build();
    }

    @Override
    public List<ChatMessageDTO> getChatMessagesBySessionIdRecently(String sessionId, int limit) {
        List<ChatMessage> chatMessages = chatMessageMapper.selectBySessionIdRecently(sessionId, limit);
        List<ChatMessageDTO> result = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            try {
                ChatMessageDTO dto = chatMessageConverter.toDTO(chatMessage);
                result.add(dto);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Override
    public CreateChatMessageResponse createChatMessage(CreateChatMessageRequest request) {
        ChatMessage chatMessage = doCreateChatMessage(request);
        publisher.publishEvent(new ChatEvent(
                        request.getAgentId(),
                        chatMessage.getSessionId(),
                        chatMessage.getContent()
                )
        );
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    @Override
    public CreateChatMessageResponse createChatMessage(ChatMessageDTO chatMessageDTO) {
        ChatMessage chatMessage = doCreateChatMessage(chatMessageDTO);
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    @Override
    public CreateChatMessageResponse agentCreateChatMessage(CreateChatMessageRequest request) {
        ChatMessage chatMessage = doCreateChatMessage(request);
        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessage.getId())
                .build();
    }

    private ChatMessage doCreateChatMessage(CreateChatMessageRequest request) {
        ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(request);
        return doCreateChatMessage(chatMessageDTO);
    }

    private ChatMessage doCreateChatMessage(ChatMessageDTO chatMessageDTO) {
        try {
            ChatMessage chatMessage = chatMessageConverter.toEntity(chatMessageDTO);

            LocalDateTime now = LocalDateTime.now();
            chatMessage.setCreatedAt(now);
            chatMessage.setUpdatedAt(now);

            int result = chatMessageMapper.insert(chatMessage);
            if (result <= 0) {
                throw new BizException("鍒涘缓鑱婂ぉ娑堟伅澶辫触");
            }

            sessionMemoryCache.append(chatMessageDTO.getSessionId(), memoryMessageMapper.toMessage(chatMessageDTO));
            return chatMessage;
        } catch (JsonProcessingException e) {
            throw new BizException("鍒涘缓鑱婂ぉ娑堟伅鏃跺彂鐢熷簭鍒楀寲閿欒: " + e.getMessage());
        }
    }

    @Override
    public CreateChatMessageResponse appendChatMessage(String chatMessageId, String appendContent) {
        ChatMessage existingChatMessage = chatMessageMapper.selectById(chatMessageId);
        if (existingChatMessage == null) {
            throw new BizException("鑱婂ぉ娑堟伅涓嶅瓨鍦? " + chatMessageId);
        }

        String currentContent = existingChatMessage.getContent() != null
                ? existingChatMessage.getContent()
                : "";
        String updatedContent = currentContent + appendContent;

        ChatMessage updatedChatMessage = ChatMessage.builder()
                .id(existingChatMessage.getId())
                .sessionId(existingChatMessage.getSessionId())
                .role(existingChatMessage.getRole())
                .content(updatedContent)
                .metadata(existingChatMessage.getMetadata())
                .createdAt(existingChatMessage.getCreatedAt())
                .updatedAt(LocalDateTime.now())
                .build();

        int result = chatMessageMapper.updateById(updatedChatMessage);
        if (result <= 0) {
            throw new BizException("杩藉姞鑱婂ぉ娑堟伅鍐呭澶辫触");
        }

        sessionMemoryCache.invalidate(existingChatMessage.getSessionId());

        return CreateChatMessageResponse.builder()
                .chatMessageId(chatMessageId)
                .build();
    }

    @Override
    public void deleteChatMessage(String chatMessageId) {
        ChatMessage chatMessage = chatMessageMapper.selectById(chatMessageId);
        if (chatMessage == null) {
            throw new BizException("鑱婂ぉ娑堟伅涓嶅瓨鍦? " + chatMessageId);
        }

        int result = chatMessageMapper.deleteById(chatMessageId);
        if (result <= 0) {
            throw new BizException("鍒犻櫎鑱婂ぉ娑堟伅澶辫触");
        }

        sessionMemoryCache.invalidate(chatMessage.getSessionId());
    }

    @Override
    public void updateChatMessage(String chatMessageId, UpdateChatMessageRequest request) {
        try {
            ChatMessage existingChatMessage = chatMessageMapper.selectById(chatMessageId);
            if (existingChatMessage == null) {
                throw new BizException("鑱婂ぉ娑堟伅涓嶅瓨鍦? " + chatMessageId);
            }

            ChatMessageDTO chatMessageDTO = chatMessageConverter.toDTO(existingChatMessage);
            chatMessageConverter.updateDTOFromRequest(chatMessageDTO, request);

            ChatMessage updatedChatMessage = chatMessageConverter.toEntity(chatMessageDTO);
            updatedChatMessage.setId(existingChatMessage.getId());
            updatedChatMessage.setSessionId(existingChatMessage.getSessionId());
            updatedChatMessage.setRole(existingChatMessage.getRole());
            updatedChatMessage.setCreatedAt(existingChatMessage.getCreatedAt());
            updatedChatMessage.setUpdatedAt(LocalDateTime.now());

            int result = chatMessageMapper.updateById(updatedChatMessage);
            if (result <= 0) {
                throw new BizException("鏇存柊鑱婂ぉ娑堟伅澶辫触");
            }

            sessionMemoryCache.invalidate(existingChatMessage.getSessionId());
        } catch (JsonProcessingException e) {
            throw new BizException("鏇存柊鑱婂ぉ娑堟伅鏃跺彂鐢熷簭鍒楀寲閿欒: " + e.getMessage());
        }
    }
}
