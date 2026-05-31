package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.converter.DiagnosisReportConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.DiagnosisReportMapper;
import com.kama.jchatmind.model.entity.DiagnosisReport;
import com.kama.jchatmind.model.response.GetDiagnosisReportResponse;
import com.kama.jchatmind.model.response.GetDiagnosisReportsResponse;
import com.kama.jchatmind.model.vo.DiagnosisReportVO;
import com.kama.jchatmind.service.DiagnosisReportFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DiagnosisReportFacadeServiceImpl implements DiagnosisReportFacadeService {

    private final DiagnosisReportMapper diagnosisReportMapper;
    private final DiagnosisReportConverter diagnosisReportConverter;

    @Override
    public GetDiagnosisReportsResponse getDiagnosisReports() {
        List<DiagnosisReportVO> result = new ArrayList<>();
        for (DiagnosisReport report : diagnosisReportMapper.selectAll()) {
            result.add(diagnosisReportConverter.toVO(diagnosisReportConverter.toDTO(report)));
        }
        return GetDiagnosisReportsResponse.builder()
                .reports(result.toArray(new DiagnosisReportVO[0]))
                .build();
    }

    @Override
    public GetDiagnosisReportResponse getDiagnosisReport(String reportId) {
        DiagnosisReport report = diagnosisReportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException("诊断报告不存在: " + reportId);
        }
        return GetDiagnosisReportResponse.builder()
                .report(diagnosisReportConverter.toVO(diagnosisReportConverter.toDTO(report)))
                .build();
    }
}
