package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisEvidenceDTO {
    private String id;
    private String runId;
    private String evidenceType;
    private String title;
    private String content;
    private Double score;
    private MetaData metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
        private Double frequencyHz;
        private Double amplitude;
        private String source;
    }
}
