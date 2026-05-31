package com.kama.jchatmind.service.diagnosis;

import com.kama.jchatmind.model.dto.AnalysisRunDTO;
import com.kama.jchatmind.model.dto.DiagnosisTaskDTO;
import com.kama.jchatmind.model.dto.AnalysisEvidenceDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class DiagnosisReportBuilder {

    public String buildMarkdown(
            DiagnosisTaskDTO task,
            AnalysisRunDTO run,
            List<AnalysisEvidenceDTO> evidence
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 诊断报告\n\n");
        builder.append("## 任务信息\n");
        builder.append("- 任务名称：").append(task.getTitle()).append('\n');
        builder.append("- 设备/机组：").append(task.getDeviceName() != null ? task.getDeviceName() : "-").append('\n');
        builder.append("- 运行编号：").append(run.getRunNo()).append('\n');
        builder.append("- 风险等级：").append(run.getRiskLevel()).append("\n\n");

        builder.append("## 结论摘要\n");
        builder.append(run.getSummary() != null ? run.getSummary() : "-").append("\n\n");

        builder.append("## 证据清单\n");
        if (evidence.isEmpty()) {
            builder.append("- 暂无结构化证据\n");
        } else {
            for (AnalysisEvidenceDTO item : evidence) {
                builder.append("- ").append(item.getTitle()).append("：")
                        .append(item.getContent() != null ? item.getContent() : "-").append('\n');
            }
        }

        if (run.getMetadata() != null && run.getMetadata().getAppliedRules() != null
                && !run.getMetadata().getAppliedRules().isEmpty()) {
            builder.append("\n## 规则依据\n");
            for (DiagnosisTaskDTO.AppliedRuleSummary appliedRule : run.getMetadata().getAppliedRules()) {
                builder.append("- ")
                        .append(appliedRule.getMetricKey())
                        .append("：")
                        .append(appliedRule.getRuleName());
                if (appliedRule.getThresholdWarn() != null) {
                    builder.append("，warn=").append(String.format("%.3f", appliedRule.getThresholdWarn()));
                }
                if (appliedRule.getThresholdAlert() != null) {
                    builder.append("，alert=").append(String.format("%.3f", appliedRule.getThresholdAlert()));
                }
                if (StringUtils.hasText(appliedRule.getSourceTitle())) {
                    builder.append("，来源=").append(appliedRule.getSourceTitle());
                }
                builder.append('\n');
            }
        }

        if (run.getMetadata() != null && StringUtils.hasText(run.getMetadata().getRecommendation())) {
            builder.append("\n## 建议动作\n");
            builder.append(run.getMetadata().getRecommendation()).append('\n');
        }
        return builder.toString();
    }
}
