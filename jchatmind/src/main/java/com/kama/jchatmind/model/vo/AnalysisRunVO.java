package com.kama.jchatmind.model.vo;

import com.kama.jchatmind.model.dto.AnalysisRunDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AnalysisRunVO {
    private String id;
    private String taskId;
    private Integer runNo;
    private String status;
    private String riskLevel;
    private String summary;
    private AnalysisRunDTO.MetaData metadata;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
