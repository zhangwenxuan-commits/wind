package com.kama.jchatmind.model.response;

import com.kama.jchatmind.model.vo.AnalysisRunVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetAnalysisRunsResponse {
    private AnalysisRunVO[] runs;
}
