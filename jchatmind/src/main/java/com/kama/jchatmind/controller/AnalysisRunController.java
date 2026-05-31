package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.response.GetAnalysisEvidenceResponse;
import com.kama.jchatmind.model.response.GetAnalysisRunsResponse;
import com.kama.jchatmind.service.AnalysisRunFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class AnalysisRunController {

    private final AnalysisRunFacadeService analysisRunFacadeService;

    @GetMapping("/diagnosis-tasks/{taskId}/analysis-runs")
    public ApiResponse<GetAnalysisRunsResponse> getAnalysisRunsByTaskId(@PathVariable String taskId) {
        return ApiResponse.success(analysisRunFacadeService.getAnalysisRunsByTaskId(taskId));
    }

    @GetMapping("/analysis-runs/{runId}/evidence")
    public ApiResponse<GetAnalysisEvidenceResponse> getAnalysisEvidenceByRunId(@PathVariable String runId) {
        return ApiResponse.success(analysisRunFacadeService.getAnalysisEvidenceByRunId(runId));
    }
}
