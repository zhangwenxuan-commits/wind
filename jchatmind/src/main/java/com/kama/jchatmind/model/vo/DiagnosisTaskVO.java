package com.kama.jchatmind.model.vo;

import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.model.vo.AnalysisRunVO;
import com.kama.jchatmind.model.vo.DiagnosisReportVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DiagnosisTaskVO {
    private String id;
    private String title;
    private String deviceName;
    private String status;
    private String riskLevel;
    private String summary;
    private String symptomHint;
    private String referenceShaft;
    private String envelopeBandHint;
    private Boolean confirmed;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private DocumentVO vibrationAsset;
    private DocumentVO speedAsset;
    private ParameterTemplateVO parameterTemplate;
    private KnowledgeBaseVO parameterSource;
    private DiagnosisTaskDTO.AnalysisSnapshot latestAnalysis;
    private AnalysisRunVO latestRun;
    private DiagnosisReportVO latestReport;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
