package com.kama.jchatmind.service;

import com.kama.jchatmind.model.response.GetAnalysisEvidenceResponse;
import com.kama.jchatmind.model.response.GetAnalysisRunsResponse;

public interface AnalysisRunFacadeService {
    GetAnalysisRunsResponse getAnalysisRunsByTaskId(String taskId);

    GetAnalysisEvidenceResponse getAnalysisEvidenceByRunId(String runId);
}
