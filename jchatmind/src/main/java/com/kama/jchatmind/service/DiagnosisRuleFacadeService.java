package com.kama.jchatmind.service;

import com.kama.jchatmind.model.response.GetDiagnosisRuleResponse;
import com.kama.jchatmind.model.response.GetDiagnosisRulesResponse;

public interface DiagnosisRuleFacadeService {
    GetDiagnosisRulesResponse getDiagnosisRules();

    GetDiagnosisRuleResponse getDiagnosisRule(String ruleId);
}
