package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.DiagnosisRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiagnosisRuleMapper {
    DiagnosisRule selectById(String id);

    List<DiagnosisRule> selectAll();

    List<DiagnosisRule> selectActiveByType(@Param("ruleType") String ruleType);
}
