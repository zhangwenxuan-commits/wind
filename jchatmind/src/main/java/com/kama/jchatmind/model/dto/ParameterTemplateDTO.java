package com.kama.jchatmind.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ParameterTemplateDTO {
    private String id;
    private String name;
    private String deviceModel;
    private Integer version;
    private String status;
    private String referenceShaft;
    private String envelopeBandHint;
    private Content content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class Content {
        private BearingGeometry bearingGeometry;
        private Thresholds thresholds;
        private String notes;
    }

    @Data
    public static class BearingGeometry {
        private Integer rollingElementCount;
        private Double rollingElementDiameterMm;
        private Double pitchDiameterMm;
        private Double contactAngleDeg;
    }

    @Data
    public static class Thresholds {
        private Double crestFactorWarn;
        private Double kurtosisWarn;
        private Double highFrequencyEnergyRatioWarn;
    }
}
