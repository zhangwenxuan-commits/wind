package com.kama.jchatmind.service.diagnosis;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class DiagnosisThresholdProfile {

    private double crestFactorWarn;
    private double crestFactorAlert;
    private double kurtosisWarn;
    private double kurtosisAlert;
    private double highFrequencyEnergyRatioWarn;
    private double highFrequencyEnergyRatioAlert;
    private double candidateConfidenceBoostThreshold;
    private List<AppliedRule> appliedRules;

    public static DiagnosisThresholdProfile defaults() {
        return DiagnosisThresholdProfile.builder()
                .crestFactorWarn(4.5)
                .crestFactorAlert(6.0)
                .kurtosisWarn(4.5)
                .kurtosisAlert(6.0)
                .highFrequencyEnergyRatioWarn(0.45)
                .highFrequencyEnergyRatioAlert(0.55)
                .candidateConfidenceBoostThreshold(0.8)
                .appliedRules(new ArrayList<>())
                .build();
    }

    @Data
    @Builder
    public static class AppliedRule {
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
