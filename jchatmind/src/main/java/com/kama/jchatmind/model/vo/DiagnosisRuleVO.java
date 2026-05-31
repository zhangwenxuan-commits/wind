package com.kama.jchatmind.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class DiagnosisRuleVO {
    private String id;
    private String ruleCode;
    private String name;
    private String status;
    private String ruleType;
    private String applicability;
    private String componentScope;
    private String signalDomain;
    private String metricKey;
    private String comparator;
    private Double thresholdWarn;
    private Double thresholdAlert;
    private String frequencyBandHint;
    private String patternText;
    private String recommendation;
    private String sourceTitle;
    private String sourceUrl;
    private String sourcePublishedAt;
    private String provenance;
    private String importBatch;
    private String notes;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
