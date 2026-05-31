package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.AnalysisEvidenceConverter;
import com.kama.jchatmind.converter.AnalysisRunConverter;
import com.kama.jchatmind.mapper.AnalysisEvidenceMapper;
import com.kama.jchatmind.mapper.AnalysisRunMapper;
import com.kama.jchatmind.model.entity.AnalysisEvidence;
import com.kama.jchatmind.model.entity.AnalysisRun;
import com.kama.jchatmind.model.response.GetAnalysisEvidenceResponse;
import com.kama.jchatmind.model.response.GetAnalysisRunsResponse;
import com.kama.jchatmind.model.vo.AnalysisEvidenceVO;
import com.kama.jchatmind.model.vo.AnalysisRunVO;
import com.kama.jchatmind.service.AnalysisRunFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AnalysisRunFacadeServiceImpl implements AnalysisRunFacadeService {

    private final AnalysisRunMapper analysisRunMapper;
    private final AnalysisEvidenceMapper analysisEvidenceMapper;
    private final AnalysisRunConverter analysisRunConverter;
    private final AnalysisEvidenceConverter analysisEvidenceConverter;

    @Override
    public GetAnalysisRunsResponse getAnalysisRunsByTaskId(String taskId) {
        List<AnalysisRunVO> result = new ArrayList<>();
        for (AnalysisRun run : analysisRunMapper.selectByTaskId(taskId)) {
            try {
                result.add(analysisRunConverter.toVO(analysisRunConverter.toDTO(run)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetAnalysisRunsResponse.builder()
                .runs(result.toArray(new AnalysisRunVO[0]))
                .build();
    }

    @Override
    public GetAnalysisEvidenceResponse getAnalysisEvidenceByRunId(String runId) {
        List<AnalysisEvidenceVO> result = new ArrayList<>();
        for (AnalysisEvidence evidence : analysisEvidenceMapper.selectByRunId(runId)) {
            try {
                result.add(analysisEvidenceConverter.toVO(analysisEvidenceConverter.toDTO(evidence)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetAnalysisEvidenceResponse.builder()
                .evidence(result.toArray(new AnalysisEvidenceVO[0]))
                .build();
    }
}
