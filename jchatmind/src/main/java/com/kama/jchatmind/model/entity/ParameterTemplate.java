package com.kama.jchatmind.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParameterTemplate {
    private String id;
    private String name;
    private String deviceModel;
    private Integer version;
    private String status;
    private String referenceShaft;
    private String envelopeBandHint;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
