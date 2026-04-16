package com.kama.jchatmind.agent.runtime;

import com.kama.jchatmind.model.dto.ChatMessageDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentMessageSnapshot {

    private String messageId;
    private ChatMessageDTO.RoleType role;
    private String content;
}
