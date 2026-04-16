package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.mapper.ChunkBgeM3Mapper;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.ChunkBgeM3;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.service.DocumentStorageService;
import com.kama.jchatmind.service.MarkdownParserService;
import com.kama.jchatmind.service.RagService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Slf4j
public class RagServiceImpl implements RagService {

    private final WebClient webClient;
    private final ChunkBgeM3Mapper chunkBgeM3Mapper;
    private final DocumentMapper documentMapper;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final DocumentConverter documentConverter;
    private final String embeddingModel;
    private final boolean autoIndexOnSearch;

    public RagServiceImpl(
            WebClient.Builder builder,
            ChunkBgeM3Mapper chunkBgeM3Mapper,
            DocumentMapper documentMapper,
            DocumentStorageService documentStorageService,
            MarkdownParserService markdownParserService,
            DocumentConverter documentConverter,
            @Value("${rag.embedding.base-url:http://localhost:11434}") String embeddingBaseUrl,
            @Value("${rag.embedding.model:bge-m3}") String embeddingModel,
            @Value("${rag.embedding.auto-index-on-search:true}") boolean autoIndexOnSearch
    ) {
        this.webClient = builder.baseUrl(embeddingBaseUrl).build();
        this.chunkBgeM3Mapper = chunkBgeM3Mapper;
        this.documentMapper = documentMapper;
        this.documentStorageService = documentStorageService;
        this.markdownParserService = markdownParserService;
        this.documentConverter = documentConverter;
        this.embeddingModel = embeddingModel;
        this.autoIndexOnSearch = autoIndexOnSearch;
    }

    @Data
    private static class LegacyEmbeddingResponse {
        private float[] embedding;
    }

    @Data
    private static class EmbedResponse {
        private List<float[]> embeddings;
    }

    private float[] doEmbed(String text) {
        Assert.hasText(text, "Embedding text cannot be blank");
        try {
            return embedWithModernApi(text);
        } catch (WebClientResponseException.NotFound notFound) {
            log.warn("Embedding endpoint /api/embed not found, fallback to /api/embeddings");
            return embedWithLegacyApi(text);
        }
    }

    @Override
    public float[] embed(String text) {
        return doEmbed(text);
    }

    @Override
    public List<String> similaritySearch(String kbId, String title) {
        if (autoIndexOnSearch) {
            ensureKnowledgeBaseIndexed(kbId);
        }

        try {
            String queryEmbedding = toPgVector(doEmbed(title));
            List<ChunkBgeM3> chunks = chunkBgeM3Mapper.similaritySearch(kbId, queryEmbedding, 3);
            return chunks.stream().map(ChunkBgeM3::getContent).toList();
        } catch (Exception e) {
            if (isEmbeddingUnavailable(e)) {
                log.warn("Embedding service unavailable, fallback to markdown keyword search. kbId={}, query={}", kbId, title);
            } else {
                log.warn("Vector similarity search failed, fallback to markdown keyword search. kbId={}, query={}",
                        kbId, title, e);
            }
            return fallbackMarkdownSearch(kbId, title, 3);
        }
    }

    private float[] embedWithModernApi(String text) {
        EmbedResponse resp = webClient.post()
                .uri("/api/embed")
                .bodyValue(Map.of(
                        "model", embeddingModel,
                        "input", text
                ))
                .retrieve()
                .bodyToMono(EmbedResponse.class)
                .block();
        Assert.notNull(resp, "Embedding response cannot be null");
        Assert.notEmpty(resp.getEmbeddings(), "Embedding list cannot be empty");
        return resp.getEmbeddings().get(0);
    }

    private float[] embedWithLegacyApi(String text) {
        LegacyEmbeddingResponse resp = webClient.post()
                .uri("/api/embeddings")
                .bodyValue(Map.of(
                        "model", embeddingModel,
                        "prompt", text
                ))
                .retrieve()
                .bodyToMono(LegacyEmbeddingResponse.class)
                .block();
        Assert.notNull(resp, "Embedding response cannot be null");
        Assert.notNull(resp.getEmbedding(), "Embedding vector cannot be null");
        return resp.getEmbedding();
    }

    private void ensureKnowledgeBaseIndexed(String kbId) {
        List<Document> documents = documentMapper.selectByKbId(kbId);
        for (Document document : documents) {
            if (!isMarkdownDocument(document)) {
                continue;
            }
            if (chunkBgeM3Mapper.countByDocId(document.getId()) > 0) {
                continue;
            }
            try {
                indexMarkdownDocument(document);
            } catch (Exception e) {
                logIndexFailure(kbId, document, e);
            }
        }
    }

    private List<String> fallbackMarkdownSearch(String kbId, String query, int limit) {
        if (!StringUtils.hasText(kbId) || !StringUtils.hasText(query)) {
            return List.of();
        }

        List<ScoredMarkdownSection> scoredSections = new ArrayList<>();
        List<Document> documents = documentMapper.selectByKbId(kbId);
        for (Document document : documents) {
            if (!isMarkdownDocument(document)) {
                continue;
            }
            for (MarkdownParserService.MarkdownSection section : loadMarkdownSections(document)) {
                int score = scoreSection(query, section);
                if (score <= 0) {
                    continue;
                }
                scoredSections.add(new ScoredMarkdownSection(section, score));
            }
        }

        return scoredSections.stream()
                .sorted(Comparator.comparingInt(ScoredMarkdownSection::score).reversed())
                .limit(limit)
                .map(scored -> formatSection(scored.section()))
                .toList();
    }

    private List<MarkdownParserService.MarkdownSection> loadMarkdownSections(Document document) {
        try {
            DocumentDTO dto = documentConverter.toDTO(document);
            if (dto.getMetadata() == null || !StringUtils.hasText(dto.getMetadata().getFilePath())) {
                return List.of();
            }

            Path path = documentStorageService.getFilePath(dto.getMetadata().getFilePath());
            if (!Files.exists(path)) {
                return List.of();
            }

            try (InputStream inputStream = Files.newInputStream(path)) {
                return markdownParserService.parseMarkdown(inputStream);
            }
        } catch (Exception e) {
            log.warn("Fallback markdown search skipped one document. documentId={}, filename={}",
                    document.getId(), document.getFilename(), e);
            return List.of();
        }
    }

    private int scoreSection(String query, MarkdownParserService.MarkdownSection section) {
        String normalizedQuery = normalizeText(query);
        if (!StringUtils.hasText(normalizedQuery) || section == null) {
            return 0;
        }

        String normalizedTitle = normalizeText(section.getTitle());
        String normalizedContent = normalizeText(section.getContent());
        int score = 0;

        if (StringUtils.hasText(normalizedTitle) && normalizedTitle.contains(normalizedQuery)) {
            score += 20;
        }
        if (StringUtils.hasText(normalizedContent) && normalizedContent.contains(normalizedQuery)) {
            score += 12;
        }

        for (String token : extractQueryTokens(normalizedQuery)) {
            if (StringUtils.hasText(normalizedTitle) && normalizedTitle.contains(token)) {
                score += 6;
            }
            if (StringUtils.hasText(normalizedContent) && normalizedContent.contains(token)) {
                score += 3;
            }
        }

        return score;
    }

    private List<String> extractQueryTokens(String normalizedQuery) {
        return Stream.of(normalizedQuery.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fff]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private String formatSection(MarkdownParserService.MarkdownSection section) {
        String title = section != null ? section.getTitle() : null;
        String content = section != null ? section.getContent() : null;

        if (StringUtils.hasText(title) && StringUtils.hasText(content)) {
            return "## " + title + "\n" + content;
        }
        if (StringUtils.hasText(title)) {
            return "## " + title;
        }
        return content != null ? content : "";
    }

    private String buildChunkMetadata(String title) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
                    "title", title
            ));
        } catch (Exception e) {
            log.warn("Failed to serialize chunk metadata. title={}", title, e);
            String safeTitle = title == null ? "" : title.replace("\\", "\\\\").replace("\"", "\\\"");
            return "{\"title\":\"" + safeTitle + "\"}";
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void logIndexFailure(String kbId, Document document, Exception e) {
        if (isEmbeddingUnavailable(e)) {
            log.warn("Skip auto-index because embedding service is unavailable. kbId={}, documentId={}, filename={}",
                    kbId, document.getId(), document.getFilename());
            return;
        }
        log.error("Failed to auto-index markdown document: kbId={}, documentId={}, filename={}",
                kbId, document.getId(), document.getFilename(), e);
    }

    private boolean isEmbeddingUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.springframework.web.reactive.function.client.WebClientRequestException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.nio.channels.ClosedChannelException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isMarkdownDocument(Document document) {
        if (document == null || !StringUtils.hasText(document.getFiletype())) {
            return false;
        }
        return "md".equalsIgnoreCase(document.getFiletype())
                || "markdown".equalsIgnoreCase(document.getFiletype());
    }

    private void indexMarkdownDocument(Document document) throws Exception {
        DocumentDTO dto = documentConverter.toDTO(document);
        if (dto.getMetadata() == null || !StringUtils.hasText(dto.getMetadata().getFilePath())) {
            log.warn("Skip auto-index because file path is missing: documentId={}", document.getId());
            return;
        }

        Path path = documentStorageService.getFilePath(dto.getMetadata().getFilePath());
        if (!Files.exists(path)) {
            log.warn("Skip auto-index because file does not exist: documentId={}, path={}", document.getId(), path);
            return;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            List<MarkdownParserService.MarkdownSection> sections = markdownParserService.parseMarkdown(inputStream);
            LocalDateTime now = LocalDateTime.now();
            int chunkCount = 0;

            for (MarkdownParserService.MarkdownSection section : sections) {
                String title = section.getTitle();
                if (!StringUtils.hasText(title)) {
                    continue;
                }

                ChunkBgeM3 chunk = ChunkBgeM3.builder()
                        .kbId(document.getKbId())
                        .docId(document.getId())
                        .content(section.getContent() != null ? section.getContent() : "")
                        .metadata(buildChunkMetadata(title))
                        .embedding(doEmbed(title))
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                chunkBgeM3Mapper.insert(chunk);
                chunkCount++;
            }

            log.info("Auto-indexed markdown document: documentId={}, chunks={}", document.getId(), chunkCount);
        }
    }

    private String toPgVector(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(v[i]);
            if (i < v.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private record ScoredMarkdownSection(MarkdownParserService.MarkdownSection section, int score) {
    }
}
