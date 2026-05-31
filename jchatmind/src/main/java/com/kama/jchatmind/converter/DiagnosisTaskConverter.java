package com.kama.jchatmind.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.model.entity.DiagnosisTask;
import com.kama.jchatmind.model.request.CreateDiagnosisTaskRequest;
import com.kama.jchatmind.model.request.UpdateDiagnosisTaskRequest;
import com.kama.jchatmind.model.vo.AnalysisRunVO;
import com.kama.jchatmind.model.vo.DiagnosisTaskVO;
import com.kama.jchatmind.model.vo.DiagnosisReportVO;
import com.kama.jchatmind.model.vo.DocumentVO;
import com.kama.jchatmind.model.vo.KnowledgeBaseVO;
import com.kama.jchatmind.model.vo.ParameterTemplateVO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@AllArgsConstructor
public class DiagnosisTaskConverter {

    private final ObjectMapper objectMapper;

    public DiagnosisTask toEntity(DiagnosisTaskDTO dto) throws JsonProcessingException {
        Assert.notNull(dto, "DiagnosisTaskDTO cannot be null");

        return DiagnosisTask.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .deviceName(dto.getDeviceName())
                .status(dto.getStatus())
                .riskLevel(dto.getRiskLevel())
                .vibrationDocumentId(dto.getVibrationDocumentId())
                .speedDocumentId(dto.getSpeedDocumentId())
                .parameterTemplateId(dto.getParameterTemplateId())
                .parameterKbId(dto.getParameterKbId())
                .summary(dto.getSummary())
                .metadata(dto.getMetadata() != null ? objectMapper.writeValueAsString(dto.getMetadata()) : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public DiagnosisTaskDTO toDTO(DiagnosisTask entity) throws JsonProcessingException {
        Assert.notNull(entity, "DiagnosisTask cannot be null");

        return DiagnosisTaskDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .deviceName(entity.getDeviceName())
                .status(entity.getStatus())
                .riskLevel(entity.getRiskLevel())
                .vibrationDocumentId(entity.getVibrationDocumentId())
                .speedDocumentId(entity.getSpeedDocumentId())
                .parameterTemplateId(entity.getParameterTemplateId())
                .parameterKbId(entity.getParameterKbId())
                .summary(entity.getSummary())
                .metadata(entity.getMetadata() != null
                        ? objectMapper.readValue(entity.getMetadata(), DiagnosisTaskDTO.MetaData.class)
                        : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public DiagnosisTaskDTO toDTO(CreateDiagnosisTaskRequest request) {
        Assert.notNull(request, "CreateDiagnosisTaskRequest cannot be null");

        DiagnosisTaskDTO.MetaData metadata = new DiagnosisTaskDTO.MetaData();
        metadata.setSymptomHint(request.getSymptomHint());
        metadata.setReferenceShaft(request.getReferenceShaft());
        metadata.setEnvelopeBandHint(request.getEnvelopeBandHint());
        metadata.setConfirmed(Boolean.FALSE);

        return DiagnosisTaskDTO.builder()
                .title(request.getTitle())
                .deviceName(request.getDeviceName())
                .vibrationDocumentId(request.getVibrationDocumentId())
                .speedDocumentId(request.getSpeedDocumentId())
                .parameterTemplateId(request.getParameterTemplateId())
                .parameterKbId(request.getParameterKbId())
                .metadata(metadata)
                .build();
    }

    public void updateDTOFromRequest(DiagnosisTaskDTO dto, UpdateDiagnosisTaskRequest request) {
        Assert.notNull(dto, "DiagnosisTaskDTO cannot be null");
        Assert.notNull(request, "UpdateDiagnosisTaskRequest cannot be null");

        if (request.getTitle() != null) {
            dto.setTitle(request.getTitle());
        }
        if (request.getDeviceName() != null) {
            dto.setDeviceName(request.getDeviceName());
        }
        if (request.getVibrationDocumentId() != null) {
            dto.setVibrationDocumentId(request.getVibrationDocumentId());
        }
        if (request.getSpeedDocumentId() != null) {
            dto.setSpeedDocumentId(request.getSpeedDocumentId());
        }
        if (request.getParameterTemplateId() != null) {
            dto.setParameterTemplateId(request.getParameterTemplateId());
        }
        if (request.getParameterKbId() != null) {
            dto.setParameterKbId(request.getParameterKbId());
        }
        DiagnosisTaskDTO.MetaData metadata = dto.getMetadata();
        if (metadata == null) {
            metadata = new DiagnosisTaskDTO.MetaData();
            dto.setMetadata(metadata);
        }
        if (request.getSymptomHint() != null) {
            metadata.setSymptomHint(request.getSymptomHint());
        }
        if (request.getReferenceShaft() != null) {
            metadata.setReferenceShaft(request.getReferenceShaft());
        }
        if (request.getEnvelopeBandHint() != null) {
            metadata.setEnvelopeBandHint(request.getEnvelopeBandHint());
        }
    }

    public DiagnosisTaskVO toVO(
            DiagnosisTaskDTO dto,
            DocumentVO vibrationAsset,
            DocumentVO speedAsset,
            ParameterTemplateVO parameterTemplate,
            KnowledgeBaseVO parameterSource,
            AnalysisRunVO latestRun,
            DiagnosisReportVO latestReport
    ) {
        DiagnosisTaskDTO.MetaData metadata = dto.getMetadata();
        return DiagnosisTaskVO.builder()
                .id(dto.getId())
                .title(dto.getTitle())
                .deviceName(dto.getDeviceName())
                .status(dto.getStatus())
                .riskLevel(dto.getRiskLevel())
                .summary(dto.getSummary())
                .symptomHint(metadata != null ? metadata.getSymptomHint() : null)
                .referenceShaft(metadata != null ? metadata.getReferenceShaft() : null)
                .envelopeBandHint(metadata != null ? metadata.getEnvelopeBandHint() : null)
                .confirmed(metadata != null ? metadata.getConfirmed() : null)
                .confirmedBy(metadata != null ? metadata.getConfirmedBy() : null)
                .confirmedAt(metadata != null ? metadata.getConfirmedAt() : null)
                .latestAnalysis(metadata != null ? metadata.getLatestAnalysis() : null)
                .vibrationAsset(vibrationAsset)
                .speedAsset(speedAsset)
                .parameterTemplate(parameterTemplate)
                .parameterSource(parameterSource)
                .latestRun(latestRun)
                .latestReport(latestReport)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
