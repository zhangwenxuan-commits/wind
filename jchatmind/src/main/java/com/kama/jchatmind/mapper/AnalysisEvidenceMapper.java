package com.kama.jchatmind.mapper;

import com.kama.jchatmind.model.entity.AnalysisEvidence;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AnalysisEvidenceMapper {
    int insert(AnalysisEvidence analysisEvidence);

    List<AnalysisEvidence> selectByRunId(String runId);
}
