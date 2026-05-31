package com.kama.jchatmind.converter;

import com.kama.jchatmind.model.dto.DiagnosisReportDTO;
import com.kama.jchatmind.model.entity.DiagnosisReport;
import com.kama.jchatmind.model.vo.DiagnosisReportVO;
import org.springframework.stereotype.Component;

@Component
public class DiagnosisReportConverter {

    public DiagnosisReport toEntity(DiagnosisReportDTO dto) {
        return DiagnosisReport.builder()
                .id(dto.getId())
                .taskId(dto.getTaskId())
                .runId(dto.getRunId())
                .version(dto.getVersion())
                .status(dto.getStatus())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .contentMarkdown(dto.getContentMarkdown())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }

    public DiagnosisReportDTO toDTO(DiagnosisReport entity) {
        return DiagnosisReportDTO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .runId(entity.getRunId())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .contentMarkdown(entity.getContentMarkdown())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public DiagnosisReportVO toVO(DiagnosisReportDTO dto) {
        return DiagnosisReportVO.builder()
                .id(dto.getId())
                .taskId(dto.getTaskId())
                .runId(dto.getRunId())
                .version(dto.getVersion())
                .status(dto.getStatus())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .contentMarkdown(dto.getContentMarkdown())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .build();
    }
}
