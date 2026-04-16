package com.kama.jchatmind.service;

import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.service.vibration.VibrationModels;

import java.io.IOException;
import java.util.List;

public interface VibrationAnalysisService {
    String DOCUMENT_KIND_VIBRATION_MAT = "VIBRATION_MAT";
    String PROCESSING_STATUS_PENDING = "PENDING";
    String PROCESSING_STATUS_READY = "READY";
    String PROCESSING_STATUS_FAILED = "FAILED";

    DocumentDTO.VibrationMeta inspectMatDocument(String filePath) throws IOException;
    List<VibrationModels.DocumentSummary> listReadyDocuments(String kbId);
    VibrationModels.SpectrumAnalysis analyzeSpectrum(String documentId) throws IOException;
    VibrationModels.EnvelopeSpectrumAnalysis analyzeEnvelopeSpectrum(String documentId, String bandHint) throws IOException;
    VibrationModels.SpeedSignalAnalysis analyzeSpeedSignal(String documentId) throws IOException;
    VibrationModels.OrderSpectrumAnalysis analyzeOrderSpectrum(
            String vibrationDocumentId,
            String speedDocumentId,
            String referenceShaft
    ) throws IOException;
    VibrationModels.WindTurbineReferenceProfile buildWindTurbineReferenceProfile(
            String referenceShaft,
            double referenceRpm
    );
    VibrationModels.WindTurbineReferenceMatchReport matchWindTurbineReferenceProfile(
            String vibrationDocumentId,
            String speedDocumentId,
            String referenceShaft,
            boolean useEnvelope,
            double toleranceRatio,
            String bandHint
    ) throws IOException;
    VibrationModels.BearingCharacteristicFrequencies calculateBearingCharacteristicFrequencies(
            double shaftFrequencyHz,
            int rollingElementCount,
            double rollingElementDiameterMm,
            double pitchDiameterMm,
            double contactAngleDeg
    );
    VibrationModels.DiagnosisResult diagnose(String documentId, String symptomHint) throws IOException;
}
