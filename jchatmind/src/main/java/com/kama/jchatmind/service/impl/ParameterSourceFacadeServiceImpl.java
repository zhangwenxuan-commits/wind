package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.model.response.GetParameterSourcesResponse;
import com.kama.jchatmind.model.vo.KnowledgeBaseVO;
import com.kama.jchatmind.service.ParameterSourceFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ParameterSourceFacadeServiceImpl implements ParameterSourceFacadeService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseConverter knowledgeBaseConverter;

    @Override
    public GetParameterSourcesResponse getParameterSources() {
        List<KnowledgeBaseVO> result = new ArrayList<>();
        for (KnowledgeBase knowledgeBase : knowledgeBaseMapper.selectAll()) {
            if (SignalAssetFacadeServiceImpl.SIGNAL_ASSET_KB_NAME.equals(knowledgeBase.getName())) {
                continue;
            }
            try {
                result.add(knowledgeBaseConverter.toVO(knowledgeBase));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return GetParameterSourcesResponse.builder()
                .parameterSources(result.toArray(new KnowledgeBaseVO[0]))
                .build();
    }
}
