package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.model.dto.AnalysisRunDTO;
import com.kama.jchatmind.model.entity.AnalysisRun;
import com.kama.jchatmind.model.vo.AnalysisRunVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class AnalysisRunConverter {

    private final ObjectMapper objectMapper;

    public AnalysisRun toEntity(AnalysisRunDTO dto) throws JsonProcessingException {
        return AnalysisRun.builder()
                .id(dto.getId())
                .taskId(dto.getTaskId())
                .runNo(dto.getRunNo())
                .status(dto.getStatus())
                .riskLevel(dto.getRiskLevel())
                .summary(dto.getSummary())
                .metadata(dto.getMetadata() != null ? objectMapper.writeValueAsString(dto.getMetadata()) : null)
                .startedAt(dto.getStartedAt())
                .finishedAt(dto.getFinishedAt())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public AnalysisRunDTO toDTO(AnalysisRun entity) throws JsonProcessingException {
        return AnalysisRunDTO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .runNo(entity.getRunNo())
                .status(entity.getStatus())
                .riskLevel(entity.getRiskLevel())
                .summary(entity.getSummary())
                .metadata(entity.getMetadata() != null
                        ? objectMapper.readValue(entity.getMetadata(), AnalysisRunDTO.MetaData.class)
                        : null)
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public AnalysisRunVO toVO(AnalysisRunDTO dto) {
        return AnalysisRunVO.builder()
                .id(dto.getId())
                .taskId(dto.getTaskId())
                .runNo(dto.getRunNo())
                .status(dto.getStatus())
                .riskLevel(dto.getRiskLevel())
                .summary(dto.getSummary())
                .metadata(dto.getMetadata())
                .startedAt(dto.getStartedAt())
                .finishedAt(dto.getFinishedAt())
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
