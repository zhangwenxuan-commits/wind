package com.kama.jchatmind.service.diagnosis;

import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.service.VibrationAnalysisService;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosisTaskAnalyzerTest {

    @Test
    void shouldBuildHighRiskAnalysisSnapshot() throws IOException {
        VibrationAnalysisService vibrationAnalysisService = mock(VibrationAnalysisService.class);
        DiagnosisTaskAnalyzer analyzer = new DiagnosisTaskAnalyzer(vibrationAnalysisService);

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setCrestFactor(6.2);
        basicStats.setKurtosis(5.4);

        when(vibrationAnalysisService.analyzeSpectrum(eq("doc-vib")))
                .thenReturn(VibrationModels.SpectrumAnalysis.builder()
                        .documentId("doc-vib")
                        .basicStats(basicStats)
                        .highFrequencyEnergyRatio(0.62)
                        .dominantPeaks(List.of(
                                VibrationModels.SpectrumPeak.builder()
                                        .frequencyHz(120.5)
                                        .amplitude(8.9)
                                        .build()
                        ))
                        .build());

        when(vibrationAnalysisService.diagnose(eq("doc-vib"), eq("冲击类故障"), any(DiagnosisThresholdProfile.class)))
                .thenReturn(VibrationModels.DiagnosisResult.builder()
                        .summary("存在明显冲击特征，建议优先复核 HSS 轴承。")
                        .candidates(List.of(
                                VibrationModels.DiagnosisCandidate.builder()
                                        .label("HSS 轴承外圈故障")
                                        .confidence(0.91)
                                        .recommendation("建议停机窗口内安排复核并检查润滑状态。")
                                        .evidence(List.of("包络特征明显", "高频能量异常"))
                                        .build()
                        ))
                        .build());

        when(vibrationAnalysisService.analyzeSpeedSignal(eq("doc-speed")))
                .thenReturn(VibrationModels.SpeedSignalAnalysis.builder()
                        .averageRpm(1785.2)
                        .equivalentFrequencyHz(29.75)
                        .build());

        DiagnosisTaskDTO.MetaData metaData = new DiagnosisTaskDTO.MetaData();
        metaData.setSymptomHint("冲击类故障");
        metaData.setReferenceShaft("HSS");
        DiagnosisTaskDTO task = DiagnosisTaskDTO.builder()
                .vibrationDocumentId("doc-vib")
                .speedDocumentId("doc-speed")
                .metadata(metaData)
                .build();

        DiagnosisThresholdProfile profile = DiagnosisThresholdProfile.defaults();
        profile.setAppliedRules(List.of(
                DiagnosisThresholdProfile.AppliedRule.builder()
                        .ruleCode("RULE_KURTOSIS")
                        .ruleName("Kurtosis threshold")
                        .ruleType("THRESHOLD")
                        .metricKey("KURTOSIS")
                        .thresholdWarn(4.0)
                        .thresholdAlert(6.0)
                        .sourceTitle("test-source")
                        .provenance("WEB_PRIMARY")
                        .build()
        ));

        DiagnosisTaskAnalyzer.AnalysisResult result = analyzer.analyze(task, profile);

        assertEquals("HIGH", result.getRiskLevel());
        assertEquals("存在明显冲击特征，建议优先复核 HSS 轴承。", result.getSummary());
        assertEquals("建议停机窗口内安排复核并检查润滑状态。", result.getSnapshot().getRecommendation());
        assertEquals(1, result.getSnapshot().getDominantPeaks().size());
        assertEquals(1785.2, result.getSnapshot().getSpeedSummary().getAverageRpm(), 1e-6);
        assertTrue(result.getSnapshot().getEvidence().stream().anyMatch(item -> item.contains("候选故障")));
        assertTrue(result.getSnapshot().getEvidence().stream().anyMatch(item -> item.contains("平均转速")));
        assertEquals(1, result.getSnapshot().getAppliedRules().size());
    }

    @Test
    void shouldReturnLowRiskForLightweightSignal() {
        DiagnosisTaskAnalyzer analyzer = new DiagnosisTaskAnalyzer(mock(VibrationAnalysisService.class));

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setCrestFactor(2.1);
        basicStats.setKurtosis(2.6);

        String riskLevel = analyzer.resolveRiskLevel(
                VibrationModels.SpectrumAnalysis.builder()
                        .basicStats(basicStats)
                        .highFrequencyEnergyRatio(0.12)
                        .build(),
                VibrationModels.DiagnosisResult.builder()
                        .candidates(List.of(
                                VibrationModels.DiagnosisCandidate.builder()
                                        .confidence(0.35)
                                        .build()
                        ))
                        .build()
                ,
                DiagnosisThresholdProfile.defaults()
        );

        assertEquals("LOW", riskLevel);
    }
}
