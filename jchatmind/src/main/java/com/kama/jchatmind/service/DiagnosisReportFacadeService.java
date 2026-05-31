package com.kama.jchatmind.service;

import com.kama.jchatmind.model.response.GetDiagnosisReportResponse;
import com.kama.jchatmind.model.response.GetDiagnosisReportsResponse;

public interface DiagnosisReportFacadeService {
    GetDiagnosisReportsResponse getDiagnosisReports();

    GetDiagnosisReportResponse getDiagnosisReport(String reportId);
}
