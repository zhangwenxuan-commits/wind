package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.DiagnosisReportVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetDiagnosisReportsResponse {
    private DiagnosisReportVO[] reports;
}
