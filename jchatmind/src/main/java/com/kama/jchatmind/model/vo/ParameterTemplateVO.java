package com.kama.jchatmind.model.vo;

import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParameterTemplateVO {
    private String id;
    private String name;
    private String deviceModel;
    private Integer version;
    private String status;
    private String referenceShaft;
    private String envelopeBandHint;
    private ParameterTemplateDTO.Content content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
