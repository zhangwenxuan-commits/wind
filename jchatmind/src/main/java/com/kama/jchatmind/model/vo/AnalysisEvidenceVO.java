package com.kama.jchatmind.model.vo;

import com.kama.jchatmind.model.dto.AnalysisEvidenceDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisEvidenceVO {
    private String id;
    private String runId;
    private String evidenceType;
    private String title;
    private String content;
    private Double score;
    private AnalysisEvidenceDTO.MetaData metadata;
    private LocalDateTime createdAt;
}
