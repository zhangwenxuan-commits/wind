package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DocumentDTO {
    private String id;

    private String kbId;

    private String filename;

    private String filetype;

    private Long size;

    private MetaData metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Data
    public static class MetaData {
        private String filePath; // 文件存储路径
        private String documentKind;//文件种类
        private String processingStatus;
        private String parseError;
        private VibrationMeta vibration;
    }
    @Data
    public static class VibrationMeta {
        private String signalName;
        private Double sampleRate;
        private Integer sampleCount;
        private Double durationSeconds;
        private String unit;
        private String deviceName;
        private List<String> availableSignals;
        private String defaultSpeedSignalName;
        private Boolean hasSpeedSignal;
        private Boolean hasVibrationSignal;
        private BasicStats basicStats;
    }
    @Data
    public static class BasicStats {
        private Double mean;
        private Double rms;
        private Double standardDeviation;
        private Double peakAbs;
        private Double peakToPeak;
        private Double crestFactor;
        private Double kurtosis;
    }
}
