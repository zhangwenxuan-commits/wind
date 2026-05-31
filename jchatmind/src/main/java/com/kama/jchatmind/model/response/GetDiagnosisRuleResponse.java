package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.DiagnosisRuleVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDiagnosisRuleResponse {
    private DiagnosisRuleVO rule;
}
