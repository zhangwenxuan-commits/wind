package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DiagnosisTaskDTO {
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

    private MetaData metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
        private String symptomHint;
        private String referenceShaft;
        private String envelopeBandHint;
        private Boolean confirmed;
        private String confirmedBy;
        private LocalDateTime confirmedAt;
        private AnalysisSnapshot latestAnalysis;
    }

    @Data
    public static class AnalysisSnapshot {
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private DocumentDTO.BasicStats basicStats;
        private List<PeakSummary> dominantPeaks;
        private SpeedSummary speedSummary;
        private List<String> evidence;
        private List<AppliedRuleSummary> appliedRules;
        private String recommendation;
        private String conclusion;
    }

    @Data
    public static class PeakSummary {
        private double frequencyHz;
        private double amplitude;
    }

    @Data
    public static class SpeedSummary {
        private double averageRpm;
        private double equivalentFrequencyHz;
    }

    @Data
    public static class AppliedRuleSummary {
        private String ruleCode;
        private String ruleName;
        private String ruleType;
        private String metricKey;
        private Double thresholdWarn;
        private Double thresholdAlert;
        private String sourceTitle;
        private String sourceUrl;
        private String sourcePublishedAt;
        private String provenance;
        private String notes;
    }
}
