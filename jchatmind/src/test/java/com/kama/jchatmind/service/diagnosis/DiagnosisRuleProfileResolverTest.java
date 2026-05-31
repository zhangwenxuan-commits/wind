package com.kama.jchatmind.service.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.DiagnosisRuleConverter;
import com.kama.jchatmind.mapper.DiagnosisRuleMapper;
import com.kama.jchatmind.model.entity.DiagnosisRule;
import com.kama.jchatmind.model.dto.ParameterTemplateDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisRuleProfileResolverTest {

    @Test
    void shouldResolveDatabaseThresholdsAndTemplateOverrides() {
        DiagnosisRuleMapper diagnosisRuleMapper = mock(DiagnosisRuleMapper.class);
        DiagnosisRuleProfileResolver resolver = new DiagnosisRuleProfileResolver(
                diagnosisRuleMapper,
                new DiagnosisRuleConverter(new ObjectMapper().findAndRegisterModules())
        );

        when(diagnosisRuleMapper.selectActiveByType(eq("THRESHOLD"))).thenReturn(List.of(
                DiagnosisRule.builder()
                        .id("rule-1")
                        .ruleCode("RULE_KURTOSIS")
                        .name("Kurtosis alarm")
                        .status("ACTIVE")
                        .ruleType("THRESHOLD")
                        .metricKey("KURTOSIS")
                        .thresholdWarn(4.0)
                        .thresholdAlert(6.0)
                        .sourceTitle("web-rule")
                        .sourceUrl("https://example.com/rule")
                        .provenance("WEB_PRIMARY")
                        .build()
        ));

        ParameterTemplateDTO.Thresholds thresholds = new ParameterTemplateDTO.Thresholds();
        thresholds.setCrestFactorWarn(4.2);
        ParameterTemplateDTO.Content content = new ParameterTemplateDTO.Content();
        content.setThresholds(thresholds);
        ParameterTemplateDTO template = ParameterTemplateDTO.builder()
                .name("WTB template")
                .content(content)
                .build();

        DiagnosisThresholdProfile profile = resolver.resolve(template);

        assertEquals(4.2, profile.getCrestFactorWarn(), 1e-6);
        assertEquals(4.0, profile.getKurtosisWarn(), 1e-6);
        assertEquals(3, profile.getAppliedRules().size());
        assertEquals("PARAMETER_TEMPLATE", profile.getAppliedRules().stream()
                .filter(rule -> "CREST_FACTOR".equals(rule.getMetricKey()))
                .findFirst()
                .orElseThrow()
                .getProvenance());
    }
}
