package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AnalysisRunDTO {
    private String id;
    private String taskId;
    private Integer runNo;
    private String status;
    private String riskLevel;
    private String summary;
    private MetaData metadata;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
        private DocumentDTO.BasicStats basicStats;
        private List<String> evidence;
        private List<DiagnosisTaskDTO.AppliedRuleSummary> appliedRules;
        private String recommendation;
        private String conclusion;
    }
}
