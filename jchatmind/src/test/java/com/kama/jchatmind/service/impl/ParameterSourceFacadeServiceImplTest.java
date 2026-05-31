package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.KnowledgeBaseConverter;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.model.response.GetParameterSourcesResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParameterSourceFacadeServiceImplTest {

    @Test
    void shouldFilterOutSystemAssetKnowledgeBase() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        when(knowledgeBaseMapper.selectAll()).thenReturn(List.of(
                KnowledgeBase.builder().id("kb-1").name("Winds 数据资产").build(),
                KnowledgeBase.builder().id("kb-2").name("风机轴承参数库").build()
        ));

        ParameterSourceFacadeServiceImpl service = new ParameterSourceFacadeServiceImpl(
                knowledgeBaseMapper,
                new KnowledgeBaseConverter(new ObjectMapper().findAndRegisterModules())
        );

        GetParameterSourcesResponse response = service.getParameterSources();

        assertEquals(1, response.getParameterSources().length);
        assertEquals("风机轴承参数库", response.getParameterSources()[0].getName());
    }
}
