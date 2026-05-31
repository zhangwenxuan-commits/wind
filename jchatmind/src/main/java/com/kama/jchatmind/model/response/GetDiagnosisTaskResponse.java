package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.DiagnosisTaskVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDiagnosisTaskResponse {
    private DiagnosisTaskVO task;
}
