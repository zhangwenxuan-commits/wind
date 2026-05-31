package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.model.dto.AnalysisEvidenceDTO;
import com.kama.jchatmind.model.entity.AnalysisEvidence;
import com.kama.jchatmind.model.vo.AnalysisEvidenceVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AnalysisEvidenceConverter {

    private final ObjectMapper objectMapper;

    public AnalysisEvidence toEntity(AnalysisEvidenceDTO dto) throws JsonProcessingException {
        return AnalysisEvidence.builder()
                .id(dto.getId())
                .runId(dto.getRunId())
                .evidenceType(dto.getEvidenceType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .score(dto.getScore())
                .metadata(dto.getMetadata() != null ? objectMapper.writeValueAsString(dto.getMetadata()) : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public AnalysisEvidenceDTO toDTO(AnalysisEvidence entity) throws JsonProcessingException {
        return AnalysisEvidenceDTO.builder()
                .id(entity.getId())
                .runId(entity.getRunId())
                .evidenceType(entity.getEvidenceType())
                .title(entity.getTitle())
                .content(entity.getContent())
                .score(entity.getScore())
                .metadata(entity.getMetadata() != null
                        ? objectMapper.readValue(entity.getMetadata(), AnalysisEvidenceDTO.MetaData.class)
                        : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public AnalysisEvidenceVO toVO(AnalysisEvidenceDTO dto) {
        return AnalysisEvidenceVO.builder()
                .id(dto.getId())
                .runId(dto.getRunId())
                .evidenceType(dto.getEvidenceType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .score(dto.getScore())
                .metadata(dto.getMetadata())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
