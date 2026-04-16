package com.kama.jchatmind.agent.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kama.jchatmind.service.VibrationAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VibrationAnalysisTool implements Tool {

    private final VibrationAnalysisService vibrationAnalysisService;
    private final ObjectMapper objectMapper;

    public VibrationAnalysisTool(
            VibrationAnalysisService vibrationAnalysisService,
            ObjectMapper objectMapper
    ) {
        this.vibrationAnalysisService = vibrationAnalysisService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "vibrationAnalysisTool";
    }

    @Override
    public String getDescription() {
        return "Provides MAT signal analysis tools for vibration, speed, order-spectrum, and wind-turbine reference-frequency matching.";
    }

    @Override
    public ToolType getType() {
        return ToolType.OPTIONAL;
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "listVibrationDocuments",
            description = "List ready MAT signal documents in a knowledge base, including vibration and speed/tachometer candidates. Input: kbId."
    )
    public String listVibrationDocuments(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            return "Error: kbId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.listReadyDocuments(kbId.trim()));
        } catch (Exception e) {
            log.error("Failed to list vibration documents", e);
            return "Error: failed to list vibration documents - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "analyzeVibrationSpectrum",
            description = "Run base spectrum analysis for a MAT vibration document. Input: documentId."
    )
    public String analyzeVibrationSpectrum(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return "Error: documentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.analyzeSpectrum(documentId.trim()));
        } catch (Exception e) {
            log.error("Failed to analyze vibration spectrum", e);
            return "Error: failed to analyze vibration spectrum - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "analyzeEnvelopeSpectrum",
            description = "Run envelope spectrum analysis for a MAT vibration document. Inputs: documentId, bandHint(optional, e.g. 2000-8000)."
    )
    public String analyzeEnvelopeSpectrum(String documentId, String bandHint) {
        if (documentId == null || documentId.isBlank()) {
            return "Error: documentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.analyzeEnvelopeSpectrum(documentId.trim(), bandHint));
        } catch (Exception e) {
            log.error("Failed to analyze envelope spectrum", e);
            return "Error: failed to analyze envelope spectrum - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "analyzeSpeedSignal",
            description = "Analyze a speed or rpm signal MAT document. Input: documentId."
    )
    public String analyzeSpeedSignal(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return "Error: documentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.analyzeSpeedSignal(documentId.trim()));
        } catch (Exception e) {
            log.error("Failed to analyze speed signal", e);
            return "Error: failed to analyze speed signal - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "analyzeOrderSpectrum",
            description = "Convert vibration spectrum to order spectrum using a speed signal. Inputs: vibrationDocumentId, speedDocumentId, referenceShaft(MS or HSS)."
    )
    public String analyzeOrderSpectrum(String vibrationDocumentId, String speedDocumentId, String referenceShaft) {
        if (vibrationDocumentId == null || vibrationDocumentId.isBlank()) {
            return "Error: vibrationDocumentId must not be blank.";
        }
        if (speedDocumentId == null || speedDocumentId.isBlank()) {
            return "Error: speedDocumentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.analyzeOrderSpectrum(
                    vibrationDocumentId.trim(),
                    speedDocumentId.trim(),
                    referenceShaft
            ));
        } catch (Exception e) {
            log.error("Failed to analyze order spectrum", e);
            return "Error: failed to analyze order spectrum - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "buildWindTurbineReferenceProfile",
            description = "Expand the built-in wind-turbine parameter card to actual reference frequencies. Inputs: referenceShaft(MS or HSS), referenceRpm."
    )
    public String buildWindTurbineReferenceProfile(String referenceShaft, double referenceRpm) {
        if (referenceRpm <= 0) {
            return "Error: referenceRpm must be greater than zero.";
        }

        try {
            return toJson(vibrationAnalysisService.buildWindTurbineReferenceProfile(referenceShaft, referenceRpm));
        } catch (Exception e) {
            log.error("Failed to build wind turbine reference profile", e);
            return "Error: failed to build wind turbine reference profile - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "matchWindTurbineReferenceProfile",
            description = "Match observed spectrum peaks against built-in wind-turbine reference frequencies. Inputs: vibrationDocumentId, speedDocumentId, referenceShaft(MS or HSS), useEnvelope, toleranceRatio, bandHint(optional)."
    )
    public String matchWindTurbineReferenceProfile(
            String vibrationDocumentId,
            String speedDocumentId,
            String referenceShaft,
            boolean useEnvelope,
            double toleranceRatio,
            String bandHint
    ) {
        if (vibrationDocumentId == null || vibrationDocumentId.isBlank()) {
            return "Error: vibrationDocumentId must not be blank.";
        }
        if (speedDocumentId == null || speedDocumentId.isBlank()) {
            return "Error: speedDocumentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.matchWindTurbineReferenceProfile(
                    vibrationDocumentId.trim(),
                    speedDocumentId.trim(),
                    referenceShaft,
                    useEnvelope,
                    toleranceRatio,
                    bandHint
            ));
        } catch (Exception e) {
            log.error("Failed to match wind turbine reference profile", e);
            return "Error: failed to match wind turbine reference profile - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "calculateBearingCharacteristicFrequencies",
            description = "Calculate FTF, BSF, BPFO, and BPFI from shaft frequency and bearing geometry."
    )
    public String calculateBearingCharacteristicFrequencies(
            double shaftFrequencyHz,
            int rollingElementCount,
            double rollingElementDiameterMm,
            double pitchDiameterMm,
            double contactAngleDeg
    ) {
        if (shaftFrequencyHz <= 0 || rollingElementCount <= 0 || rollingElementDiameterMm <= 0 || pitchDiameterMm <= 0) {
            return "Error: shaft frequency and bearing geometry must be positive.";
        }

        try {
            return toJson(vibrationAnalysisService.calculateBearingCharacteristicFrequencies(
                    shaftFrequencyHz,
                    rollingElementCount,
                    rollingElementDiameterMm,
                    pitchDiameterMm,
                    contactAngleDeg
            ));
        } catch (Exception e) {
            log.error("Failed to calculate bearing characteristic frequencies", e);
            return "Error: failed to calculate bearing characteristic frequencies - " + e.getMessage();
        }
    }

    @org.springframework.ai.tool.annotation.Tool(
            name = "diagnoseVibration",
            description = "Run heuristic vibration diagnosis for a MAT vibration document. Inputs: documentId, symptomHint(optional)."
    )
    public String diagnoseVibration(String documentId, String symptomHint) {
        if (documentId == null || documentId.isBlank()) {
            return "Error: documentId must not be blank.";
        }

        try {
            return toJson(vibrationAnalysisService.diagnose(documentId.trim(), symptomHint));
        } catch (Exception e) {
            log.error("Failed to diagnose vibration", e);
            return "Error: failed to diagnose vibration - " + e.getMessage();
        }
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }
}
