package com.kama.jchatmind.agent;

import com.kama.jchatmind.agent.tools.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DiagnosisRuntimeTools {

    static final String DIAGNOSIS_TOOL_NAME = "vibrationAnalysisTool";

    private DiagnosisRuntimeTools() {
    }

    static List<Tool> resolve(
            List<Tool> fixedTools,
            List<Tool> optionalTools,
            List<String> allowedToolNames
    ) {
        Map<String, Tool> optionalToolMap = optionalTools.stream()
                .collect(LinkedHashMap::new, (map, tool) -> map.put(tool.getName(), tool), Map::putAll);

        Tool diagnosisTool = optionalToolMap.get(DIAGNOSIS_TOOL_NAME);
        if (diagnosisTool == null) {
            throw new IllegalStateException("Diagnosis runtime requires tool: " + DIAGNOSIS_TOOL_NAME);
        }

        Map<String, Tool> runtimeTools = new LinkedHashMap<>();
        for (Tool tool : fixedTools) {
            runtimeTools.put(tool.getName(), tool);
        }
        runtimeTools.put(diagnosisTool.getName(), diagnosisTool);

        if (allowedToolNames != null) {
            for (String toolName : allowedToolNames) {
                Tool tool = optionalToolMap.get(toolName);
                if (tool != null) {
                    runtimeTools.put(tool.getName(), tool);
                }
            }
        }

        return new ArrayList<>(runtimeTools.values());
    }
}
