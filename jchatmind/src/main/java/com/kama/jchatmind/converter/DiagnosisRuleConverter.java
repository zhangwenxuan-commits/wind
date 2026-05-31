package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.model.dto.DiagnosisRuleDTO;
import com.kama.jchatmind.model.entity.DiagnosisRule;
import com.kama.jchatmind.model.vo.DiagnosisRuleVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@AllArgsConstructor
public class DiagnosisRuleConverter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public DiagnosisRule toEntity(DiagnosisRuleDTO dto) throws JsonProcessingException {
        return DiagnosisRule.builder()
                .id(dto.getId())
                .ruleCode(dto.getRuleCode())
                .name(dto.getName())
                .status(dto.getStatus())
                .ruleType(dto.getRuleType())
                .applicability(dto.getApplicability())
                .componentScope(dto.getComponentScope())
                .signalDomain(dto.getSignalDomain())
                .metricKey(dto.getMetricKey())
                .comparator(dto.getComparator())
                .thresholdWarn(dto.getThresholdWarn())
                .thresholdAlert(dto.getThresholdAlert())
                .frequencyBandHint(dto.getFrequencyBandHint())
                .patternText(dto.getPatternText())
                .recommendation(dto.getRecommendation())
                .sourceTitle(dto.getSourceTitle())
                .sourceUrl(dto.getSourceUrl())
                .sourcePublishedAt(dto.getSourcePublishedAt())
                .provenance(dto.getProvenance())
                .importBatch(dto.getImportBatch())
                .notes(dto.getNotes())
                .metadata(dto.getMetadata() != null ? objectMapper.writeValueAsString(dto.getMetadata()) : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public DiagnosisRuleDTO toDTO(DiagnosisRule entity) throws JsonProcessingException {
        return DiagnosisRuleDTO.builder()
                .id(entity.getId())
                .ruleCode(entity.getRuleCode())
                .name(entity.getName())
                .status(entity.getStatus())
                .ruleType(entity.getRuleType())
                .applicability(entity.getApplicability())
                .componentScope(entity.getComponentScope())
                .signalDomain(entity.getSignalDomain())
                .metricKey(entity.getMetricKey())
                .comparator(entity.getComparator())
                .thresholdWarn(entity.getThresholdWarn())
                .thresholdAlert(entity.getThresholdAlert())
                .frequencyBandHint(entity.getFrequencyBandHint())
                .patternText(entity.getPatternText())
                .recommendation(entity.getRecommendation())
                .sourceTitle(entity.getSourceTitle())
                .sourceUrl(entity.getSourceUrl())
                .sourcePublishedAt(entity.getSourcePublishedAt())
                .provenance(entity.getProvenance())
                .importBatch(entity.getImportBatch())
                .notes(entity.getNotes())
                .metadata(entity.getMetadata() != null
                        ? objectMapper.readValue(entity.getMetadata(), MAP_TYPE)
                        : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public DiagnosisRuleVO toVO(DiagnosisRuleDTO dto) {
        return DiagnosisRuleVO.builder()
                .id(dto.getId())
                .ruleCode(dto.getRuleCode())
                .name(dto.getName())
                .status(dto.getStatus())
                .ruleType(dto.getRuleType())
                .applicability(dto.getApplicability())
                .componentScope(dto.getComponentScope())
                .signalDomain(dto.getSignalDomain())
                .metricKey(dto.getMetricKey())
                .comparator(dto.getComparator())
                .thresholdWarn(dto.getThresholdWarn())
                .thresholdAlert(dto.getThresholdAlert())
                .frequencyBandHint(dto.getFrequencyBandHint())
                .patternText(dto.getPatternText())
                .recommendation(dto.getRecommendation())
                .sourceTitle(dto.getSourceTitle())
                .sourceUrl(dto.getSourceUrl())
                .sourcePublishedAt(dto.getSourcePublishedAt())
                .provenance(dto.getProvenance())
                .importBatch(dto.getImportBatch())
                .notes(dto.getNotes())
                .metadata(dto.getMetadata())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
