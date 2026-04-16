package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.service.DocumentStorageService;
import com.kama.jchatmind.service.MarkdownParserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFallbackToMarkdownKeywordSearchWhenEmbeddingServiceIsUnavailable() throws Exception {
        ChunkBgeM3Mapper chunkBgeM3Mapper = mock(ChunkBgeM3Mapper.class);
        DocumentMapper documentMapper = mock(DocumentMapper.class);
        DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
        MarkdownParserService markdownParserService = new MarkdownParserServiceImpl();
        DocumentConverter documentConverter = new DocumentConverter(new ObjectMapper());

        Path markdownFile = tempDir.resolve("wind-turbine-bearing-parameter-card.md");
        Files.writeString(markdownFile, """
                # HSS Bearing Parameters
                recommended envelope band: 8k-10k
                reference shaft: HSS

                # Sensor Mapping
                AN8: HSS upwind bearing radial
                AN9: HSS downwind bearing radial
                """, StandardCharsets.UTF_8);

        DocumentDTO.MetaData metadata = new DocumentDTO.MetaData();
        metadata.setFilePath("kb-1/doc-1/wind-turbine-bearing-parameter-card.md");

        Document document = Document.builder()
                .id("doc-1")
                .kbId("kb-1")
                .filename("wind-turbine-bearing-parameter-card.md")
                .filetype("md")
                .metadata(new ObjectMapper().writeValueAsString(metadata))
                .build();

        when(documentMapper.selectByKbId("kb-1")).thenReturn(List.of(document));
        when(documentStorageService.getFilePath("kb-1/doc-1/wind-turbine-bearing-parameter-card.md"))
                .thenReturn(markdownFile);

        RagServiceImpl ragService = new RagServiceImpl(
                WebClient.builder(),
                chunkBgeM3Mapper,
                documentMapper,
                documentStorageService,
                markdownParserService,
                documentConverter,
                "http://localhost:65535",
                "bge-m3",
                false
        );

        List<String> results = ragService.similaritySearch("kb-1", "HSS bearing envelope band");

        assertFalse(results.isEmpty());
        assertTrue(results.get(0).contains("8k-10k"));
        assertTrue(results.get(0).contains("HSS Bearing Parameters"));
    }
}
