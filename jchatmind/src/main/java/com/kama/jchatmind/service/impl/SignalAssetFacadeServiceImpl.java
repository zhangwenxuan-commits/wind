package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.mapper.KnowledgeBaseMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.model.entity.KnowledgeBase;
import com.kama.jchatmind.model.response.CreateSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetResponse;
import com.kama.jchatmind.model.response.GetSignalAssetsResponse;
import com.kama.jchatmind.model.vo.SignalAssetVO;
import com.kama.jchatmind.service.DocumentFacadeService;
import com.kama.jchatmind.service.SignalAssetFacadeService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class SignalAssetFacadeServiceImpl implements SignalAssetFacadeService {

    static final String SIGNAL_ASSET_KB_NAME = "Winds 数据资产";
    private static final String SIGNAL_ASSET_KB_DESCRIPTION = "系统自动创建的数据资产容器，用于承载诊断信号文件。";

    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentFacadeService documentFacadeService;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public GetSignalAssetsResponse getSignalAssets() {
        List<Document> documents = documentMapper.selectByFiletype("mat");
        Map<String, KnowledgeBase> knowledgeBaseMap = buildKnowledgeBaseMap();
        List<SignalAssetVO> assets = new ArrayList<>();
        for (Document document : documents) {
            assets.add(toSignalAssetVO(document, knowledgeBaseMap.get(document.getKbId())));
        }
        return GetSignalAssetsResponse.builder()
                .assets(assets.toArray(new SignalAssetVO[0]))
                .build();
    }

    @Override
    public GetSignalAssetResponse getSignalAsset(String assetId) {
        Document document = documentMapper.selectById(assetId);
        if (document == null || !"mat".equalsIgnoreCase(document.getFiletype())) {
            throw new BizException("信号资产不存在: " + assetId);
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
        return GetSignalAssetResponse.builder()
                .asset(toSignalAssetVO(document, knowledgeBase))
                .build();
    }

    @Override
    public CreateSignalAssetResponse uploadSignalAsset(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.toLowerCase().endsWith(".mat")) {
            throw new BizException("当前仅支持上传 MAT 文件");
        }
        String kbId = ensureSignalAssetKnowledgeBase().getId();
        return CreateSignalAssetResponse.builder()
                .assetId(documentFacadeService.uploadDocument(kbId, file).getDocumentId())
                .build();
    }

    private KnowledgeBase ensureSignalAssetKnowledgeBase() {
        KnowledgeBase existing = knowledgeBaseMapper.selectByName(SIGNAL_ASSET_KB_NAME);
        if (existing != null) {
            return existing;
        }
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .name(SIGNAL_ASSET_KB_NAME)
                .description(SIGNAL_ASSET_KB_DESCRIPTION)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        int result = knowledgeBaseMapper.insert(knowledgeBase);
        if (result <= 0 || knowledgeBase.getId() == null) {
            throw new BizException("创建数据资产容器失败");
        }
        return knowledgeBase;
    }

    private Map<String, KnowledgeBase> buildKnowledgeBaseMap() {
        Map<String, KnowledgeBase> map = new HashMap<>();
        for (KnowledgeBase knowledgeBase : knowledgeBaseMapper.selectAll()) {
            map.put(knowledgeBase.getId(), knowledgeBase);
        }
        return map;
    }

    private SignalAssetVO toSignalAssetVO(Document document, KnowledgeBase knowledgeBase) {
        try {
            DocumentDTO dto = documentConverter.toDTO(document);
            DocumentDTO.MetaData metadata = dto.getMetadata();
            DocumentDTO.VibrationMeta vibrationMeta = metadata != null ? metadata.getVibration() : null;
            return SignalAssetVO.builder()
                    .id(dto.getId())
                    .filename(dto.getFilename())
                    .filetype(dto.getFiletype())
                    .size(dto.getSize())
                    .knowledgeBaseId(dto.getKbId())
                    .knowledgeBaseName(knowledgeBase != null ? knowledgeBase.getName() : null)
                    .documentKind(metadata != null ? metadata.getDocumentKind() : null)
                    .processingStatus(metadata != null ? metadata.getProcessingStatus() : null)
                    .parseError(metadata != null ? metadata.getParseError() : null)
                    .signalName(vibrationMeta != null ? vibrationMeta.getSignalName() : null)
                    .sampleRate(vibrationMeta != null ? vibrationMeta.getSampleRate() : null)
                    .unit(vibrationMeta != null ? vibrationMeta.getUnit() : null)
                    .deviceName(vibrationMeta != null ? vibrationMeta.getDeviceName() : null)
                    .availableSignals(vibrationMeta != null ? vibrationMeta.getAvailableSignals() : null)
                    .defaultSpeedSignalName(vibrationMeta != null ? vibrationMeta.getDefaultSpeedSignalName() : null)
                    .hasSpeedSignal(vibrationMeta != null ? vibrationMeta.getHasSpeedSignal() : null)
                    .hasVibrationSignal(vibrationMeta != null ? vibrationMeta.getHasVibrationSignal() : null)
                    .updatedAt(dto.getUpdatedAt())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
