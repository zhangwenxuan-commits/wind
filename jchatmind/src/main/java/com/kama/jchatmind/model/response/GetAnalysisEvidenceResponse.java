package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.AnalysisEvidenceVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetAnalysisEvidenceResponse {
    private AnalysisEvidenceVO[] evidence;
}
