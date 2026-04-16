package com.kama.jchatmind.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentVO {
    private String id;
    private String kbId;
    private String filename;
    private String filetype;
    private Long size;
    private String documentKind;
    private String processingStatus;
    private String parseError;
    private String signalName;
    private Double sampleRate;
    private String unit;
    private String deviceName;
}

