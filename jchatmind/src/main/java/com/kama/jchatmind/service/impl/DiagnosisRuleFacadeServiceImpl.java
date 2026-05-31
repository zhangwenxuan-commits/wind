package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.DiagnosisRuleConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.DiagnosisRuleMapper;
import com.kama.jchatmind.model.entity.DiagnosisRule;
import com.kama.jchatmind.model.response.GetDiagnosisRuleResponse;
import com.kama.jchatmind.model.response.GetDiagnosisRulesResponse;
import com.kama.jchatmind.model.vo.DiagnosisRuleVO;
import com.kama.jchatmind.service.DiagnosisRuleFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class DiagnosisRuleFacadeServiceImpl implements DiagnosisRuleFacadeService {

    private final DiagnosisRuleMapper diagnosisRuleMapper;
    private final DiagnosisRuleConverter diagnosisRuleConverter;

    @Override
    public GetDiagnosisRulesResponse getDiagnosisRules() {
        List<DiagnosisRuleVO> result = new ArrayList<>();
        for (DiagnosisRule rule : diagnosisRuleMapper.selectAll()) {
            result.add(toVO(rule));
        }
        return GetDiagnosisRulesResponse.builder()
                .rules(result.toArray(new DiagnosisRuleVO[0]))
                .build();
    }

    @Override
    public GetDiagnosisRuleResponse getDiagnosisRule(String ruleId) {
        DiagnosisRule rule = diagnosisRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BizException("诊断规则不存在: " + ruleId);
        }
        return GetDiagnosisRuleResponse.builder()
                .rule(toVO(rule))
                .build();
    }

    private DiagnosisRuleVO toVO(DiagnosisRule rule) {
        try {
            return diagnosisRuleConverter.toVO(diagnosisRuleConverter.toDTO(rule));
        } catch (JsonProcessingException e) {
            throw new BizException("诊断规则元数据解析失败: " + rule.getId());
        }
    }
}
