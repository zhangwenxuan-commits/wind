package com.kama.jchatmind.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SignalAssetVO {
    private String id;
    private String filename;
    private String filetype;
    private Long size;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private String documentKind;
    private String processingStatus;
    private String parseError;
    private String signalName;
    private Double sampleRate;
    private String unit;
    private String deviceName;
    private List<String> availableSignals;
    private String defaultSpeedSignalName;
    private Boolean hasSpeedSignal;
    private Boolean hasVibrationSignal;
    private LocalDateTime updatedAt;
}
