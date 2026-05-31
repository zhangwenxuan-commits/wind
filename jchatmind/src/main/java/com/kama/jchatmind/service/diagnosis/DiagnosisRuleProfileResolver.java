package com.kama.jchatmind.service.diagnosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.DiagnosisRuleConverter;
import com.kama.jchatmind.mapper.DiagnosisRuleMapper;
import com.kama.jchatmind.model.dto.DiagnosisRuleDTO;
import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import com.kama.jchatmind.model.entity.DiagnosisRule;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class DiagnosisRuleProfileResolver {

    private static final String RULE_TYPE_THRESHOLD = "THRESHOLD";
    private static final String METRIC_CREST_FACTOR = "CREST_FACTOR";
    private static final String METRIC_KURTOSIS = "KURTOSIS";
    private static final String METRIC_HIGH_FREQUENCY_ENERGY_RATIO = "HIGH_FREQUENCY_ENERGY_RATIO";

    private final DiagnosisRuleMapper diagnosisRuleMapper;
    private final DiagnosisRuleConverter diagnosisRuleConverter;

    public DiagnosisThresholdProfile resolve(ParameterTemplateDTO template) {
        DiagnosisThresholdProfile profile = DiagnosisThresholdProfile.defaults();
        Map<String, DiagnosisRuleDTO> rulesByMetric = loadThresholdRules();

        applyDatabaseRule(
                rulesByMetric.get(METRIC_CREST_FACTOR),
                profile.getCrestFactorWarn(),
                profile.getCrestFactorAlert(),
                (warn, alert) -> {
                    profile.setCrestFactorWarn(warn);
                    profile.setCrestFactorAlert(alert);
                },
                profile.getAppliedRules()
        );
        applyDatabaseRule(
                rulesByMetric.get(METRIC_KURTOSIS),
                profile.getKurtosisWarn(),
                profile.getKurtosisAlert(),
                (warn, alert) -> {
                    profile.setKurtosisWarn(warn);
                    profile.setKurtosisAlert(alert);
                },
                profile.getAppliedRules()
        );
        applyDatabaseRule(
                rulesByMetric.get(METRIC_HIGH_FREQUENCY_ENERGY_RATIO),
                profile.getHighFrequencyEnergyRatioWarn(),
                profile.getHighFrequencyEnergyRatioAlert(),
                (warn, alert) -> {
                    profile.setHighFrequencyEnergyRatioWarn(warn);
                    profile.setHighFrequencyEnergyRatioAlert(alert);
                },
                profile.getAppliedRules()
        );

        applyParameterTemplateOverrides(profile, template);
        fillMissingRuleAudit(profile);
        return profile;
    }

    private Map<String, DiagnosisRuleDTO> loadThresholdRules() {
        Map<String, DiagnosisRuleDTO> result = new LinkedHashMap<>();
        List<DiagnosisRule> rules = diagnosisRuleMapper.selectActiveByType(RULE_TYPE_THRESHOLD);
        for (DiagnosisRule rule : rules) {
            try {
                DiagnosisRuleDTO dto = diagnosisRuleConverter.toDTO(rule);
                if (dto.getMetricKey() != null && !result.containsKey(dto.getMetricKey())) {
                    result.put(dto.getMetricKey(), dto);
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("无法解析诊断规则: " + rule.getId(), e);
            }
        }
        return result;
    }

    private void applyDatabaseRule(
            DiagnosisRuleDTO rule,
            double fallbackWarn,
            double fallbackAlert,
            ThresholdSetter setter,
            List<DiagnosisThresholdProfile.AppliedRule> appliedRules
    ) {
        if (rule == null) {
            setter.accept(fallbackWarn, fallbackAlert);
            return;
        }
        double effectiveWarn = rule.getThresholdWarn() != null ? rule.getThresholdWarn() : fallbackWarn;
        double effectiveAlert = normalizeAlertThreshold(rule.getThresholdAlert(), effectiveWarn, fallbackAlert);
        setter.accept(effectiveWarn, effectiveAlert);
        appliedRules.add(toAppliedRule(rule, effectiveWarn, effectiveAlert));
    }

    private void applyParameterTemplateOverrides(DiagnosisThresholdProfile profile, ParameterTemplateDTO template) {
        if (template == null || template.getContent() == null || template.getContent().getThresholds() == null) {
            return;
        }
        ParameterTemplateDTO.Thresholds thresholds = template.getContent().getThresholds();
        if (thresholds.getCrestFactorWarn() != null) {
            profile.setCrestFactorWarn(thresholds.getCrestFactorWarn());
            upsertAppliedRule(
                    profile.getAppliedRules(),
                    templateOverride(
                            METRIC_CREST_FACTOR,
                            "参数模板阈值覆盖: Crest Factor",
                            thresholds.getCrestFactorWarn(),
                            profile.getCrestFactorAlert(),
                            template.getName()
                    )
            );
        }
        if (thresholds.getKurtosisWarn() != null) {
            profile.setKurtosisWarn(thresholds.getKurtosisWarn());
            upsertAppliedRule(
                    profile.getAppliedRules(),
                    templateOverride(
                            METRIC_KURTOSIS,
                            "参数模板阈值覆盖: Kurtosis",
                            thresholds.getKurtosisWarn(),
                            profile.getKurtosisAlert(),
                            template.getName()
                    )
            );
        }
        if (thresholds.getHighFrequencyEnergyRatioWarn() != null) {
            profile.setHighFrequencyEnergyRatioWarn(thresholds.getHighFrequencyEnergyRatioWarn());
            upsertAppliedRule(
                    profile.getAppliedRules(),
                    templateOverride(
                            METRIC_HIGH_FREQUENCY_ENERGY_RATIO,
                            "参数模板阈值覆盖: 高频能量占比",
                            thresholds.getHighFrequencyEnergyRatioWarn(),
                            profile.getHighFrequencyEnergyRatioAlert(),
                            template.getName()
                    )
            );
        }
    }

    private void fillMissingRuleAudit(DiagnosisThresholdProfile profile) {
        ensureAppliedRule(
                profile.getAppliedRules(),
                METRIC_CREST_FACTOR,
                "内置启发式阈值: Crest Factor",
                profile.getCrestFactorWarn(),
                profile.getCrestFactorAlert()
        );
        ensureAppliedRule(
                profile.getAppliedRules(),
                METRIC_KURTOSIS,
                "内置启发式阈值: Kurtosis",
                profile.getKurtosisWarn(),
                profile.getKurtosisAlert()
        );
        ensureAppliedRule(
                profile.getAppliedRules(),
                METRIC_HIGH_FREQUENCY_ENERGY_RATIO,
                "内置启发式阈值: 高频能量占比",
                profile.getHighFrequencyEnergyRatioWarn(),
                profile.getHighFrequencyEnergyRatioAlert()
        );
    }

    private void ensureAppliedRule(
            List<DiagnosisThresholdProfile.AppliedRule> appliedRules,
            String metricKey,
            String ruleName,
            double warn,
            double alert
    ) {
        for (DiagnosisThresholdProfile.AppliedRule appliedRule : appliedRules) {
            if (metricKey.equals(appliedRule.getMetricKey())) {
                return;
            }
        }
        appliedRules.add(DiagnosisThresholdProfile.AppliedRule.builder()
                .ruleCode("DEFAULT_" + metricKey)
                .ruleName(ruleName)
                .ruleType("THRESHOLD")
                .metricKey(metricKey)
                .thresholdWarn(warn)
                .thresholdAlert(alert)
                .sourceTitle("Winds built-in defaults")
                .sourceUrl(null)
                .sourcePublishedAt(null)
                .provenance("INTERNAL_DEFAULT")
                .notes("Fallback threshold because no active database rule was available.")
                .build());
    }

    private void upsertAppliedRule(
            List<DiagnosisThresholdProfile.AppliedRule> appliedRules,
            DiagnosisThresholdProfile.AppliedRule replacement
    ) {
        for (int i = 0; i < appliedRules.size(); i++) {
            DiagnosisThresholdProfile.AppliedRule existing = appliedRules.get(i);
            if (replacement.getMetricKey().equals(existing.getMetricKey())) {
                appliedRules.set(i, replacement);
                return;
            }
        }
        appliedRules.add(replacement);
    }

    private DiagnosisThresholdProfile.AppliedRule templateOverride(
            String metricKey,
            String ruleName,
            double warn,
            double alert,
            String templateName
    ) {
        return DiagnosisThresholdProfile.AppliedRule.builder()
                .ruleCode("TEMPLATE_" + metricKey)
                .ruleName(ruleName)
                .ruleType("THRESHOLD")
                .metricKey(metricKey)
                .thresholdWarn(warn)
                .thresholdAlert(alert)
                .sourceTitle(templateName)
                .sourceUrl(null)
                .sourcePublishedAt(null)
                .provenance("PARAMETER_TEMPLATE")
                .notes("Resolved from parameter_template.content.thresholds.")
                .build();
    }

    private DiagnosisThresholdProfile.AppliedRule toAppliedRule(
            DiagnosisRuleDTO rule,
            double effectiveWarn,
            double effectiveAlert
    ) {
        return DiagnosisThresholdProfile.AppliedRule.builder()
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getName())
                .ruleType(rule.getRuleType())
                .metricKey(rule.getMetricKey())
                .thresholdWarn(effectiveWarn)
                .thresholdAlert(effectiveAlert)
                .sourceTitle(rule.getSourceTitle())
                .sourceUrl(rule.getSourceUrl())
                .sourcePublishedAt(rule.getSourcePublishedAt())
                .provenance(rule.getProvenance())
                .notes(rule.getNotes())
                .build();
    }

    private double normalizeAlertThreshold(Double alert, double warn, double fallbackAlert) {
        double effectiveAlert = alert != null ? alert : fallbackAlert;
        return Math.max(effectiveAlert, warn);
    }

    @FunctionalInterface
    private interface ThresholdSetter {
        void accept(double warn, double alert);
    }
}
