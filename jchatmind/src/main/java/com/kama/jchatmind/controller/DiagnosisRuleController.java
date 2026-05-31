package com.kama.jchatmind.controller;

import com.kama.jchatmind.model.common.ApiResponse;
import com.kama.jchatmind.model.response.GetDiagnosisRuleResponse;
import com.kama.jchatmind.model.response.GetDiagnosisRulesResponse;
import com.kama.jchatmind.service.DiagnosisRuleFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class DiagnosisRuleController {

    private final DiagnosisRuleFacadeService diagnosisRuleFacadeService;

    @GetMapping("/diagnosis-rules")
    public ApiResponse<GetDiagnosisRulesResponse> getDiagnosisRules() {
        return ApiResponse.success(diagnosisRuleFacadeService.getDiagnosisRules());
    }

    @GetMapping("/diagnosis-rules/{ruleId}")
    public ApiResponse<GetDiagnosisRuleResponse> getDiagnosisRule(@PathVariable String ruleId) {
        return ApiResponse.success(diagnosisRuleFacadeService.getDiagnosisRule(ruleId));
    }
}
