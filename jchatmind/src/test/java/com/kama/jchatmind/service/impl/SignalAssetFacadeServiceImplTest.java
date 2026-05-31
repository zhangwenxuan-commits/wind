package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.model.response.CreateDocumentResponse;
import com.kama.jchatmind.model.response.GetSignalAssetsResponse;
import com.kama.jchatmind.service.DocumentFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalAssetFacadeServiceImplTest {

    private DocumentMapper documentMapper;
    private DocumentFacadeService documentFacadeService;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private SignalAssetFacadeServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        documentMapper = mock(DocumentMapper.class);
        documentFacadeService = mock(DocumentFacadeService.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        service = new SignalAssetFacadeServiceImpl(
                documentMapper,
                new DocumentConverter(objectMapper),
                documentFacadeService,
                knowledgeBaseMapper
        );
    }

    @Test
    void shouldMapSignalAssetsFromDocuments() throws Exception {
        DocumentDTO.MetaData metaData = new DocumentDTO.MetaData();
        metaData.setDocumentKind("VIBRATION_MAT");
        metaData.setProcessingStatus("READY");
        DocumentDTO.VibrationMeta vibrationMeta = new DocumentDTO.VibrationMeta();
        vibrationMeta.setSignalName("AN7");
        vibrationMeta.setSampleRate(40000.0);
        vibrationMeta.setHasSpeedSignal(Boolean.TRUE);
        metaData.setVibration(vibrationMeta);

        Document document = new DocumentConverter(new ObjectMapper().findAndRegisterModules()).toEntity(
                DocumentDTO.builder()
                        .id("doc-1")
                        .kbId("kb-1")
                        .filename("bearing.mat")
                        .filetype("mat")
                        .size(1024L)
                        .metadata(metaData)
                        .updatedAt(LocalDateTime.now())
                        .build()
        );
        when(documentMapper.selectByFiletype(eq("mat"))).thenReturn(List.of(document));
        when(knowledgeBaseMapper.selectAll())
                .thenReturn(List.of(KnowledgeBase.builder().id("kb-1").name("Winds 数据资产").build()));

        GetSignalAssetsResponse response = service.getSignalAssets();

        assertEquals(1, response.getAssets().length);
        assertEquals("AN7", response.getAssets()[0].getSignalName());
        assertEquals(Boolean.TRUE, response.getAssets()[0].getHasSpeedSignal());
    }

    @Test
    void shouldCreateSignalAssetKnowledgeBaseOnUpload() {
        when(knowledgeBaseMapper.selectByName(eq(SignalAssetFacadeServiceImpl.SIGNAL_ASSET_KB_NAME)))
                .thenReturn(null);
        doAnswer(invocation -> {
            KnowledgeBase knowledgeBase = invocation.getArgument(0);
            knowledgeBase.setId("kb-system");
            return 1;
        }).when(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
        when(documentFacadeService.uploadDocument(any(), any()))
                .thenReturn(CreateDocumentResponse.builder().documentId("doc-9").build());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.mat",
                "application/octet-stream",
                new byte[]{1, 2, 3}
        );

        service.uploadSignalAsset(file);

        verify(knowledgeBaseMapper).insert(any(KnowledgeBase.class));
        verify(documentFacadeService).uploadDocument(any(), eq(file));
    }
}
