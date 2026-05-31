package com.kama.jchatmind.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DiagnosisTask {
    private String id;

    private String title;

    private String deviceName;

    private String status;

    private String riskLevel;

    private String vibrationDocumentId;

    private String speedDocumentId;

    private String parameterTemplateId;

    private String parameterKbId;

    private String summary;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
