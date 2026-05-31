package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.response.GetDiagnosisReportResponse;
import com.kama.jchatmind.model.response.GetDiagnosisReportsResponse;
import com.kama.jchatmind.service.DiagnosisReportFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DiagnosisReportController {

    private final DiagnosisReportFacadeService diagnosisReportFacadeService;

    @GetMapping("/diagnosis-reports")
    public ApiResponse<GetDiagnosisReportsResponse> getDiagnosisReports() {
        return ApiResponse.success(diagnosisReportFacadeService.getDiagnosisReports());
    }

    @GetMapping("/diagnosis-reports/{reportId}")
    public ApiResponse<GetDiagnosisReportResponse> getDiagnosisReport(@PathVariable String reportId) {
        return ApiResponse.success(diagnosisReportFacadeService.getDiagnosisReport(reportId));
    }
}
