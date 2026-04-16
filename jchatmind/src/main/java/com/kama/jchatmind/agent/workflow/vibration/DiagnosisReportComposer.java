package com.kama.jchatmind.agent.workflow.vibration;

import com.kama.jchatmind.agent.runtime.SessionRuntimeState;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosisReportComposer {

    private DiagnosisReportComposer() {
    }

    public static String compose(SessionRuntimeState runtimeState) {
        DiagnosisWorkspace workspace = runtimeState != null ? runtimeState.getDiagnosisWorkspace() : null;
        if (workspace == null) {
            return """
                    诊断结论
                    - 当前缺少可用的诊断工作区数据，暂时无法生成结构化报告。

                    关键证据
                    - 未获取到诊断上下文。

                    风险等级
                    - 待确认

                    建议动作
                    - 请重新触发一次分析流程，并确认 MAT 文件与知识库都已正确加载。

                    不确定性说明
                    - 本次报告由兜底逻辑生成，因为最终报告阶段缺少足够上下文。
                    """;
        }

        String conclusion = buildConclusion(workspace);
        String riskLevel = buildRiskLevel(workspace);
        String action = buildRecommendedAction(workspace);
        String uncertainty = buildUncertainty(workspace);

        List<String> evidence = new ArrayList<>();
        if (StringUtils.hasText(workspace.getSelectedDocumentName())) {
            evidence.add("目标文件: " + workspace.getSelectedDocumentName());
        }
        if (workspace.getCrestFactor() != null) {
            evidence.add(String.format("Crest factor: %.2f", workspace.getCrestFactor()));
        }
        if (workspace.getKurtosis() != null) {
            evidence.add(String.format("Kurtosis: %.2f", workspace.getKurtosis()));
        }
        if (workspace.getHighFrequencyRatio() != null) {
            evidence.add(String.format("High-frequency energy ratio: %.2f", workspace.getHighFrequencyRatio()));
        }
        if (Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted())) {
            evidence.add("已完成包络/高级分析。");
        }
        if (Boolean.TRUE.equals(workspace.getReferenceMatchEvaluated())) {
            evidence.add(Boolean.TRUE.equals(workspace.getReferenceProfileMatched())
                    ? "已完成参考频率匹配，且存在匹配项。"
                    : "已完成参考频率匹配，但未形成强匹配证据。");
        }
        if (Boolean.TRUE.equals(workspace.getBearingFrequenciesCalculated())) {
            evidence.add("已完成轴承特征频率计算。");
        }
        if (workspace.getEvidenceNotes() != null && !workspace.getEvidenceNotes().isEmpty()) {
            List<String> notes = workspace.getEvidenceNotes().size() <= 3
                    ? workspace.getEvidenceNotes()
                    : workspace.getEvidenceNotes().subList(workspace.getEvidenceNotes().size() - 3, workspace.getEvidenceNotes().size());
            evidence.addAll(notes);
        }
        if (evidence.isEmpty()) {
            evidence.add("当前可用证据有限，未形成稳定诊断链路。");
        }

        StringBuilder report = new StringBuilder();
        report.append("诊断结论\n");
        report.append("- ").append(conclusion).append("\n\n");
        report.append("关键证据\n");
        for (String item : evidence) {
            report.append("- ").append(item).append("\n");
        }
        report.append("\n");
        report.append("风险等级\n");
        report.append("- ").append(riskLevel).append("\n\n");
        report.append("建议动作\n");
        report.append("- ").append(action).append("\n\n");
        report.append("不确定性说明\n");
        report.append("- ").append(uncertainty).append("\n");
        return report.toString().trim();
    }

    private static String buildConclusion(DiagnosisWorkspace workspace) {
        if (Boolean.TRUE.equals(workspace.getReferenceProfileMatched())) {
            return "当前振动特征与参考故障频率存在对应关系，倾向轴承相关异常。";
        }
        if (Boolean.TRUE.equals(workspace.getBearingFrequenciesCalculated())
                && Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted())) {
            return "高级分析与轴承特征频率计算均已形成异常线索，倾向轴承早期故障或润滑退化。";
        }
        if (Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted()) && hasStrongImpactEvidence(workspace)) {
            return "包络与高频冲击特征异常，存在较明显的轴承相关异常风险。";
        }
        if (hasStrongImpactEvidence(workspace)) {
            return "基础频谱已显示明显冲击和高频异常，建议按轴承异常方向优先排查。";
        }
        return "当前证据未形成明确故障定位，建议结合更多工况与转速信息复核。";
    }

    private static String buildRiskLevel(DiagnosisWorkspace workspace) {
        if (Boolean.TRUE.equals(workspace.getReferenceProfileMatched())) {
            return "高";
        }
        if (Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted()) && hasStrongImpactEvidence(workspace)) {
            return "中高";
        }
        if (hasStrongImpactEvidence(workspace)) {
            return "中";
        }
        return "待确认";
    }

    private static String buildRecommendedAction(DiagnosisWorkspace workspace) {
        if (Boolean.TRUE.equals(workspace.getReferenceProfileMatched())) {
            return "尽快安排停机窗口复核轴承状态，并结合检修记录确认是否存在磨损、剥落或润滑异常。";
        }
        if (Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted()) && hasStrongImpactEvidence(workspace)) {
            return "建议优先复核轴承润滑、游隙和安装状态，并补充转速/阶次谱或趋势数据做二次确认。";
        }
        if (hasStrongImpactEvidence(workspace)) {
            return "建议保留当前报告作为初筛结果，并补充包络、转速或工况信息后再做复判。";
        }
        return "建议补充转速、阶次谱、包络或轴承参数信息后重新分析。";
    }

    private static String buildUncertainty(DiagnosisWorkspace workspace) {
        List<String> reasons = new ArrayList<>();
        if (!Boolean.TRUE.equals(workspace.getSpeedAnalysisCompleted())
                && !Boolean.TRUE.equals(workspace.getOrderSpectrumCompleted())
                && !Boolean.TRUE.equals(workspace.getReferenceMatchEvaluated())) {
            reasons.add("本报告未包含转速、阶次谱或参考频率匹配的交叉验证。");
        }
        if (!Boolean.TRUE.equals(workspace.getBearingFrequenciesCalculated())
                && !Boolean.TRUE.equals(workspace.getReferenceProfileMatched())) {
            reasons.add("当前未形成直接的特征频率对应关系。");
        }
        if (!Boolean.TRUE.equals(workspace.getAdvancedAnalysisCompleted())) {
            reasons.add("高级分析证据不足，结论主要基于基础频谱指标。");
        }
        if (reasons.isEmpty()) {
            reasons.add("本报告由后端兜底生成，表达更保守，建议后续再补充模型化报告。");
        }
        return String.join(" ", reasons);
    }

    private static boolean hasStrongImpactEvidence(DiagnosisWorkspace workspace) {
        double crestFactor = workspace.getCrestFactor() != null ? workspace.getCrestFactor() : 0.0;
        double kurtosis = workspace.getKurtosis() != null ? workspace.getKurtosis() : 0.0;
        double highFrequencyRatio = workspace.getHighFrequencyRatio() != null ? workspace.getHighFrequencyRatio() : 0.0;
        return crestFactor >= 4.5 || kurtosis >= 4.5 || highFrequencyRatio >= 0.45;
    }
}
