package com.kama.jchatmind.service.vibration;

import com.kama.jchatmind.model.dto.DocumentDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class VibrationModels {

    private VibrationModels() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalData {
        private String signalName;
        private double[] samples;
        private double sampleRate;
        private String unit;
        private String deviceName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatSignalCatalog {
        private List<String> signalNames;
        private String defaultVibrationSignalName;
        private String defaultSpeedSignalName;
        private Double sampleRate;
        private String unit;
        private String deviceName;
        private Boolean hasVibrationSignal;
        private Boolean hasSpeedSignal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSummary {
        private String documentId;
        private String kbId;
        private String filename;
        private String deviceName;
        private String signalName;
        private Double sampleRate;
        private String unit;
        private String signalRole;
        private String roleReason;
        private String referenceShaftHint;
        private Boolean hasVibrationSignal;
        private Boolean hasSpeedSignal;
        private String defaultSpeedSignalName;
        private Integer sampleCount;
        private Double durationSeconds;
        private String processingStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpectrumPoint {
        private double frequencyHz;
        private double amplitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpectrumPeak {
        private double frequencyHz;
        private double amplitude;
        private int binIndex;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpectrumAnalysis {
        private String documentId;
        private String filename;
        private String deviceName;
        private String signalName;
        private double sampleRate;
        private String unit;
        private int sampleCount;
        private double durationSeconds;
        private DocumentDTO.BasicStats basicStats;
        private List<SpectrumPeak> dominantPeaks;
        private List<SpectrumPoint> previewSpectrum;
        private Double highFrequencyEnergyRatio;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvelopeSpectrumAnalysis {
        private String documentId;
        private String filename;
        private String deviceName;
        private String signalName;
        private double sampleRate;
        private String unit;
        private String bandHint;
        private DocumentDTO.BasicStats envelopeStats;
        private List<SpectrumPeak> dominantPeaks;
        private List<SpectrumPoint> previewSpectrum;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BearingCharacteristicFrequencies {
        private double shaftFrequencyHz;
        private int rollingElementCount;
        private double rollingElementDiameterMm;
        private double pitchDiameterMm;
        private double contactAngleDeg;
        private double cageFrequencyHz;
        private double ballSpinFrequencyHz;
        private double outerRaceFrequencyHz;
        private double innerRaceFrequencyHz;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpeedSignalAnalysis {
        private String documentId;
        private String filename;
        private String deviceName;
        private String signalName;
        private double sampleRate;
        private String unit;
        private String normalizedUnit;
        private double averageRpm;
        private double minRpm;
        private double maxRpm;
        private double stdRpm;
        private double equivalentFrequencyHz;
        private List<Double> previewRpm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSpectrumPoint {
        private double order;
        private double amplitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSpectrumPeak {
        private double order;
        private double frequencyHz;
        private double amplitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderSpectrumAnalysis {
        private String vibrationDocumentId;
        private String speedDocumentId;
        private String referenceShaft;
        private double referenceRpm;
        private double referenceFrequencyHz;
        private List<OrderSpectrumPeak> dominantOrders;
        private List<OrderSpectrumPoint> previewOrderSpectrum;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceFrequencyEntry {
        private String category;
        private String component;
        private String feature;
        private Double relativeToMainShaft;
        private Double relativeToHighSpeedShaft;
        private Double expectedFrequencyHz;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WindTurbineReferenceProfile {
        private String referenceShaft;
        private double referenceRpm;
        private double referenceFrequencyHz;
        private List<ReferenceFrequencyEntry> entries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceFrequencyMatch {
        private String category;
        private String component;
        private String feature;
        private double expectedFrequencyHz;
        private Double observedFrequencyHz;
        private Double observedAmplitude;
        private Double observedOrder;
        private Double frequencyErrorHz;
        private Double errorRatio;
        private Double orderError;
        private String matchBasis;
        private boolean matched;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WindTurbineReferenceMatchReport {
        private String vibrationDocumentId;
        private String speedDocumentId;
        private String referenceShaft;
        private boolean usedEnvelope;
        private double referenceRpm;
        private double toleranceRatio;
        private List<ReferenceFrequencyMatch> matches;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisCandidate {
        private String label;
        private double confidence;
        private List<String> evidence;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagnosisResult {
        private String documentId;
        private String filename;
        private String deviceName;
        private String signalName;
        private double sampleRate;
        private String unit;
        private String symptomHint;
        private List<DiagnosisCandidate> candidates;
        private List<String> limitations;
        private String summary;
        private LocalDateTime analyzedAt;
    }
}
