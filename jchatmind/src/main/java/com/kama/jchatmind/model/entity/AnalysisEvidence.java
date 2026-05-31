package com.kama.jchatmind.model.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisEvidence {
    private String id;
    private String runId;
    private String evidenceType;
    private String title;
    private String content;
    private Double score;
    private String metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
