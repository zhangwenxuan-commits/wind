package com.kama.jchatmind.service.diagnosis;

import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.service.VibrationAnalysisService;
import com.kama.jchatmind.service.vibration.VibrationModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class DiagnosisTaskAnalyzer {

    private static final String DEFAULT_REFERENCE_SHAFT = "HSS";

    private final VibrationAnalysisService vibrationAnalysisService;

    public AnalysisResult analyze(DiagnosisTaskDTO task) throws IOException {
        return analyze(task, DiagnosisThresholdProfile.defaults());
    }

    public AnalysisResult analyze(DiagnosisTaskDTO task, DiagnosisThresholdProfile thresholdProfile) throws IOException {
        DiagnosisTaskDTO.MetaData metadata = task.getMetadata() != null ? task.getMetadata() : new DiagnosisTaskDTO.MetaData();
        DiagnosisThresholdProfile effectiveProfile = thresholdProfile != null
                ? thresholdProfile
                : DiagnosisThresholdProfile.defaults();

        String referenceShaft = metadata.getReferenceShaft() != null ? metadata.getReferenceShaft() : DEFAULT_REFERENCE_SHAFT;

        VibrationModels.SpectrumAnalysis spectrumAnalysis =
                vibrationAnalysisService.analyzeSpectrum(task.getVibrationDocumentId());
        VibrationModels.DiagnosisResult diagnosisResult =
                vibrationAnalysisService.diagnose(task.getVibrationDocumentId(), metadata.getSymptomHint(), effectiveProfile);

        VibrationModels.SpeedSignalAnalysis speedSignalAnalysis = null;
        if (task.getSpeedDocumentId() != null && !task.getSpeedDocumentId().isBlank()) {
            speedSignalAnalysis = vibrationAnalysisService.analyzeSpeedSignal(task.getSpeedDocumentId());
        }

        DiagnosisTaskDTO.AnalysisSnapshot snapshot = new DiagnosisTaskDTO.AnalysisSnapshot();
        snapshot.setStartedAt(LocalDateTime.now());
        snapshot.setFinishedAt(LocalDateTime.now());
        snapshot.setBasicStats(spectrumAnalysis.getBasicStats());
        snapshot.setDominantPeaks(toPeakSummaries(spectrumAnalysis.getDominantPeaks()));
        snapshot.setSpeedSummary(toSpeedSummary(speedSignalAnalysis));
        snapshot.setEvidence(buildEvidence(spectrumAnalysis, diagnosisResult, speedSignalAnalysis));
        snapshot.setAppliedRules(toAppliedRuleSummaries(effectiveProfile));
        snapshot.setRecommendation(resolveRecommendation(diagnosisResult));
        snapshot.setConclusion(resolveConclusion(diagnosisResult));

        return AnalysisResult.builder()
                .riskLevel(resolveRiskLevel(spectrumAnalysis, diagnosisResult, effectiveProfile))
                .summary(resolveConclusion(diagnosisResult))
                .snapshot(snapshot)
                .build();
    }

    private List<DiagnosisTaskDTO.PeakSummary> toPeakSummaries(List<VibrationModels.SpectrumPeak> peaks) {
        List<DiagnosisTaskDTO.PeakSummary> summaries = new ArrayList<>();
        if (peaks == null) {
            return summaries;
        }
        for (int i = 0; i < Math.min(peaks.size(), 5); i++) {
            VibrationModels.SpectrumPeak peak = peaks.get(i);
            DiagnosisTaskDTO.PeakSummary summary = new DiagnosisTaskDTO.PeakSummary();
            summary.setFrequencyHz(peak.getFrequencyHz());
            summary.setAmplitude(peak.getAmplitude());
            summaries.add(summary);
        }
        return summaries;
    }

    private DiagnosisTaskDTO.SpeedSummary toSpeedSummary(VibrationModels.SpeedSignalAnalysis speedSignalAnalysis) {
        if (speedSignalAnalysis == null) {
            return null;
        }
        DiagnosisTaskDTO.SpeedSummary summary = new DiagnosisTaskDTO.SpeedSummary();
        summary.setAverageRpm(speedSignalAnalysis.getAverageRpm());
        summary.setEquivalentFrequencyHz(speedSignalAnalysis.getEquivalentFrequencyHz());
        return summary;
    }

    private List<String> buildEvidence(
            VibrationModels.SpectrumAnalysis spectrumAnalysis,
            VibrationModels.DiagnosisResult diagnosisResult,
            VibrationModels.SpeedSignalAnalysis speedSignalAnalysis
    ) {
        List<String> evidence = new ArrayList<>();
        if (spectrumAnalysis.getBasicStats() != null) {
            if (spectrumAnalysis.getBasicStats().getCrestFactor() != null) {
                evidence.add(String.format("峰值因子 %.2f", spectrumAnalysis.getBasicStats().getCrestFactor()));
            }
            if (spectrumAnalysis.getBasicStats().getKurtosis() != null) {
                evidence.add(String.format("峭度 %.2f", spectrumAnalysis.getBasicStats().getKurtosis()));
            }
        }
        if (spectrumAnalysis.getHighFrequencyEnergyRatio() != null) {
            evidence.add(String.format("高频能量占比 %.2f", spectrumAnalysis.getHighFrequencyEnergyRatio()));
        }
        if (spectrumAnalysis.getDominantPeaks() != null && !spectrumAnalysis.getDominantPeaks().isEmpty()) {
            VibrationModels.SpectrumPeak primaryPeak = spectrumAnalysis.getDominantPeaks().get(0);
            evidence.add(String.format("主峰 %.2f Hz / %.3f", primaryPeak.getFrequencyHz(), primaryPeak.getAmplitude()));
        }
        if (diagnosisResult != null && diagnosisResult.getCandidates() != null && !diagnosisResult.getCandidates().isEmpty()) {
            VibrationModels.DiagnosisCandidate candidate = diagnosisResult.getCandidates().get(0);
            evidence.add(String.format("候选故障 %s (置信度 %.2f)", candidate.getLabel(), candidate.getConfidence()));
            if (candidate.getEvidence() != null) {
                evidence.addAll(candidate.getEvidence());
            }
        }
        if (speedSignalAnalysis != null) {
            evidence.add(String.format("平均转速 %.1f rpm", speedSignalAnalysis.getAverageRpm()));
        }
        return evidence;
    }

    private List<DiagnosisTaskDTO.AppliedRuleSummary> toAppliedRuleSummaries(DiagnosisThresholdProfile profile) {
        List<DiagnosisTaskDTO.AppliedRuleSummary> result = new ArrayList<>();
        if (profile == null || profile.getAppliedRules() == null) {
            return result;
        }
        for (DiagnosisThresholdProfile.AppliedRule appliedRule : profile.getAppliedRules()) {
            DiagnosisTaskDTO.AppliedRuleSummary summary = new DiagnosisTaskDTO.AppliedRuleSummary();
            summary.setRuleCode(appliedRule.getRuleCode());
            summary.setRuleName(appliedRule.getRuleName());
            summary.setRuleType(appliedRule.getRuleType());
            summary.setMetricKey(appliedRule.getMetricKey());
            summary.setThresholdWarn(appliedRule.getThresholdWarn());
            summary.setThresholdAlert(appliedRule.getThresholdAlert());
            summary.setSourceTitle(appliedRule.getSourceTitle());
            summary.setSourceUrl(appliedRule.getSourceUrl());
            summary.setSourcePublishedAt(appliedRule.getSourcePublishedAt());
            summary.setProvenance(appliedRule.getProvenance());
            summary.setNotes(appliedRule.getNotes());
            result.add(summary);
        }
        return result;
    }

    private String resolveRecommendation(VibrationModels.DiagnosisResult diagnosisResult) {
        if (diagnosisResult != null && diagnosisResult.getCandidates() != null && !diagnosisResult.getCandidates().isEmpty()) {
            String recommendation = diagnosisResult.getCandidates().get(0).getRecommendation();
            if (recommendation != null && !recommendation.isBlank()) {
                return recommendation;
            }
        }
        return "建议复核关键峰值并结合设备工况安排复测。";
    }

    private String resolveConclusion(VibrationModels.DiagnosisResult diagnosisResult) {
        if (diagnosisResult != null && diagnosisResult.getSummary() != null && !diagnosisResult.getSummary().isBlank()) {
            return diagnosisResult.getSummary();
        }
        return "已完成基础频谱分析，请结合证据面板进行人工确认。";
    }

    String resolveRiskLevel(
            VibrationModels.SpectrumAnalysis spectrumAnalysis,
            VibrationModels.DiagnosisResult diagnosisResult,
            DiagnosisThresholdProfile thresholdProfile
    ) {
        DiagnosisThresholdProfile effectiveProfile = thresholdProfile != null
                ? thresholdProfile
                : DiagnosisThresholdProfile.defaults();
        int score = 0;
        if (spectrumAnalysis.getBasicStats() != null) {
            Double crestFactor = spectrumAnalysis.getBasicStats().getCrestFactor();
            Double kurtosis = spectrumAnalysis.getBasicStats().getKurtosis();
            if (crestFactor != null) {
                score += crestFactor >= effectiveProfile.getCrestFactorAlert()
                        ? 2
                        : crestFactor >= effectiveProfile.getCrestFactorWarn() ? 1 : 0;
            }
            if (kurtosis != null) {
                score += kurtosis >= effectiveProfile.getKurtosisAlert()
                        ? 2
                        : kurtosis >= effectiveProfile.getKurtosisWarn() ? 1 : 0;
            }
        }
        if (spectrumAnalysis.getHighFrequencyEnergyRatio() != null) {
            double ratio = spectrumAnalysis.getHighFrequencyEnergyRatio();
            score += ratio >= effectiveProfile.getHighFrequencyEnergyRatioAlert()
                    ? 2
                    : ratio >= effectiveProfile.getHighFrequencyEnergyRatioWarn() ? 1 : 0;
        }
        if (diagnosisResult != null && diagnosisResult.getCandidates() != null && !diagnosisResult.getCandidates().isEmpty()) {
            double confidence = diagnosisResult.getCandidates().get(0).getConfidence();
            score += confidence >= effectiveProfile.getCandidateConfidenceBoostThreshold() ? 1 : 0;
        }
        if (score >= 4) {
            return "HIGH";
        }
        if (score >= 2) {
            return "MEDIUM";
        }
        return "LOW";
    }

    @Data
    @Builder
    public static class AnalysisResult {
        private String riskLevel;
        private String summary;
        private DiagnosisTaskDTO.AnalysisSnapshot snapshot;
    }
}
