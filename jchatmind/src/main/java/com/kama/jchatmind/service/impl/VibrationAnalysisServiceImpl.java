package com.kama.jchatmind.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kama.jchatmind.converter.DocumentConverter;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.mapper.DocumentMapper;
import com.kama.jchatmind.model.dto.DocumentDTO;
import com.kama.jchatmind.model.entity.Document;
import com.kama.jchatmind.service.DocumentStorageService;
import com.kama.jchatmind.service.MatFileParserService;
import com.kama.jchatmind.service.VibrationAnalysisService;
import com.kama.jchatmind.service.diagnosis.DiagnosisThresholdProfile;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class VibrationAnalysisServiceImpl implements VibrationAnalysisService {

    private static final int MAX_PREVIEW_POINTS = 64;
    private static final int MAX_DOMINANT_PEAKS = 5;
    private static final int MAX_FFT_SAMPLES = 32768;
    private static final String REFERENCE_SHAFT_MAIN = "MS";
    private static final String REFERENCE_SHAFT_HIGH = "HSS";
    private static final double DEFAULT_MATCH_TOLERANCE_RATIO = 0.03;
    private static final double DEFAULT_MATCH_ORDER_TOLERANCE = 0.10;

    private final DocumentMapper documentMapper;
    private final DocumentConverter documentConverter;
    private final DocumentStorageService documentStorageService;
    private final MatFileParserService matFileParserService;

    public VibrationAnalysisServiceImpl(
            DocumentMapper documentMapper,
            DocumentConverter documentConverter,
            DocumentStorageService documentStorageService,
            MatFileParserService matFileParserService
    ) {
        this.documentMapper = documentMapper;
        this.documentConverter = documentConverter;
        this.documentStorageService = documentStorageService;
        this.matFileParserService = matFileParserService;
    }

    @Override
    public DocumentDTO.VibrationMeta inspectMatDocument(String filePath) throws IOException {
        Path path = documentStorageService.getFilePath(filePath);
        VibrationModels.MatSignalCatalog catalog = matFileParserService.inspect(path);
        VibrationModels.SignalData signalData = matFileParserService.parseVibration(path);
        DocumentDTO.BasicStats basicStats = calculateBasicStats(signalData.getSamples());

        DocumentDTO.VibrationMeta vibrationMeta = new DocumentDTO.VibrationMeta();
        vibrationMeta.setSignalName(signalData.getSignalName());
        vibrationMeta.setSampleRate(signalData.getSampleRate());
        vibrationMeta.setSampleCount(signalData.getSamples().length);
        vibrationMeta.setDurationSeconds(signalData.getSamples().length / signalData.getSampleRate());
        vibrationMeta.setUnit(signalData.getUnit());
        vibrationMeta.setDeviceName(signalData.getDeviceName());
        vibrationMeta.setAvailableSignals(catalog.getSignalNames());
        vibrationMeta.setDefaultSpeedSignalName(catalog.getDefaultSpeedSignalName());
        vibrationMeta.setHasSpeedSignal(catalog.getHasSpeedSignal());
        vibrationMeta.setHasVibrationSignal(catalog.getHasVibrationSignal());
        vibrationMeta.setBasicStats(basicStats);
        return vibrationMeta;
    }

    @Override
    public List<VibrationModels.DocumentSummary> listReadyDocuments(String kbId) {
        return documentMapper.selectByKbId(kbId).stream()
                .map(this::safeToDTO)
                .filter(this::isReadyMatDocument)
                .map(this::toDocumentSummary)
                .toList();
    }

    @Override
    public VibrationModels.SpectrumAnalysis analyzeSpectrum(String documentId) throws IOException {
        ResolvedDocument resolvedDocument = loadResolvedDocument(documentId);
        VibrationModels.SignalData signalData = loadVibrationSignal(resolvedDocument.path());
        DocumentDTO.BasicStats basicStats = calculateBasicStats(signalData.getSamples());

        SpectrumComputation spectrumComputation = computeSpectrum(signalData.getSamples(), signalData.getSampleRate());

        return VibrationModels.SpectrumAnalysis.builder()
                .documentId(resolvedDocument.document().getId())
                .filename(resolvedDocument.document().getFilename())
                .deviceName(signalData.getDeviceName())
                .signalName(signalData.getSignalName())
                .sampleRate(signalData.getSampleRate())
                .unit(signalData.getUnit())
                .sampleCount(signalData.getSamples().length)
                .durationSeconds(signalData.getSamples().length / signalData.getSampleRate())
                .basicStats(basicStats)
                .dominantPeaks(spectrumComputation.peaks())
                .previewSpectrum(spectrumComputation.preview())
                .highFrequencyEnergyRatio(estimateHighFrequencyRatio(spectrumComputation.preview(), signalData.getSampleRate()))
                .build();
    }

    @Override
    public VibrationModels.EnvelopeSpectrumAnalysis analyzeEnvelopeSpectrum(String documentId, String bandHint) throws IOException {
        ResolvedDocument resolvedDocument = loadResolvedDocument(documentId);
        VibrationModels.SignalData signalData = loadVibrationSignal(resolvedDocument.path());

        double[] envelope = buildEnvelope(signalData.getSamples(), signalData.getSampleRate(), bandHint);
        DocumentDTO.BasicStats envelopeStats = calculateBasicStats(envelope);
        SpectrumComputation spectrumComputation = computeSpectrum(envelope, signalData.getSampleRate());

        return VibrationModels.EnvelopeSpectrumAnalysis.builder()
                .documentId(resolvedDocument.document().getId())
                .filename(resolvedDocument.document().getFilename())
                .deviceName(signalData.getDeviceName())
                .signalName(signalData.getSignalName())
                .sampleRate(signalData.getSampleRate())
                .unit(signalData.getUnit())
                .bandHint(normalizeBandHint(bandHint))
                .envelopeStats(envelopeStats)
                .dominantPeaks(spectrumComputation.peaks())
                .previewSpectrum(spectrumComputation.preview())
                .build();
    }

    @Override
    public VibrationModels.SpeedSignalAnalysis analyzeSpeedSignal(String documentId) throws IOException {
        ResolvedDocument resolvedDocument = loadResolvedDocument(documentId);
        VibrationModels.SignalData signalData = loadSpeedSignal(resolvedDocument.path());

        double[] normalizedRpm = normalizeSpeedSamples(signalData.getSamples(), signalData.getUnit());
        SpeedStats speedStats = computeSpeedStats(normalizedRpm);

        return VibrationModels.SpeedSignalAnalysis.builder()
                .documentId(resolvedDocument.document().getId())
                .filename(resolvedDocument.document().getFilename())
                .deviceName(signalData.getDeviceName())
                .signalName(signalData.getSignalName())
                .sampleRate(signalData.getSampleRate())
                .unit(signalData.getUnit())
                .normalizedUnit("rpm")
                .averageRpm(speedStats.averageRpm())
                .minRpm(speedStats.minRpm())
                .maxRpm(speedStats.maxRpm())
                .stdRpm(speedStats.stdRpm())
                .equivalentFrequencyHz(speedStats.averageRpm() / 60.0)
                .previewRpm(downSampleSeries(normalizedRpm))
                .build();
    }

    @Override
    public VibrationModels.OrderSpectrumAnalysis analyzeOrderSpectrum(
            String vibrationDocumentId,
            String speedDocumentId,
            String referenceShaft
    ) throws IOException {
        VibrationModels.SpectrumAnalysis spectrumAnalysis = analyzeSpectrum(vibrationDocumentId);
        VibrationModels.SpeedSignalAnalysis speedSignalAnalysis = analyzeSpeedSignal(speedDocumentId);
        String normalizedReferenceShaft = normalizeReferenceShaft(referenceShaft);
        double referenceFrequencyHz = resolveReferenceFrequencyHz(normalizedReferenceShaft, speedSignalAnalysis);

        List<VibrationModels.OrderSpectrumPeak> dominantOrders = spectrumAnalysis.getDominantPeaks().stream()
                .map(peak -> VibrationModels.OrderSpectrumPeak.builder()
                        .order(referenceFrequencyHz > 0 ? peak.getFrequencyHz() / referenceFrequencyHz : 0.0)
                        .frequencyHz(peak.getFrequencyHz())
                        .amplitude(peak.getAmplitude())
                        .build())
                .toList();

        List<VibrationModels.OrderSpectrumPoint> previewOrders = spectrumAnalysis.getPreviewSpectrum().stream()
                .map(point -> VibrationModels.OrderSpectrumPoint.builder()
                        .order(referenceFrequencyHz > 0 ? point.getFrequencyHz() / referenceFrequencyHz : 0.0)
                        .amplitude(point.getAmplitude())
                        .build())
                .toList();

        return VibrationModels.OrderSpectrumAnalysis.builder()
                .vibrationDocumentId(vibrationDocumentId)
                .speedDocumentId(speedDocumentId)
                .referenceShaft(normalizedReferenceShaft)
                .referenceRpm(speedSignalAnalysis.getAverageRpm())
                .referenceFrequencyHz(referenceFrequencyHz)
                .dominantOrders(dominantOrders)
                .previewOrderSpectrum(previewOrders)
                .build();
    }

    @Override
    public VibrationModels.WindTurbineReferenceProfile buildWindTurbineReferenceProfile(
            String referenceShaft,
            double referenceRpm
    ) {
        String normalizedReferenceShaft = normalizeReferenceShaft(referenceShaft);
        double referenceFrequencyHz = referenceRpm / 60.0;

        List<VibrationModels.ReferenceFrequencyEntry> entries = buildReferenceCatalog().stream()
                .map(entry -> materializeReference(entry, normalizedReferenceShaft, referenceFrequencyHz))
                .filter(entry -> entry != null)
                .toList();

        return VibrationModels.WindTurbineReferenceProfile.builder()
                .referenceShaft(normalizedReferenceShaft)
                .referenceRpm(referenceRpm)
                .referenceFrequencyHz(referenceFrequencyHz)
                .entries(entries)
                .build();
    }

    @Override
    public VibrationModels.WindTurbineReferenceMatchReport matchWindTurbineReferenceProfile(
            String vibrationDocumentId,
            String speedDocumentId,
            String referenceShaft,
            boolean useEnvelope,
            double toleranceRatio,
            String bandHint
    ) throws IOException {
        VibrationModels.SpeedSignalAnalysis speedSignalAnalysis = analyzeSpeedSignal(speedDocumentId);
        VibrationModels.WindTurbineReferenceProfile referenceProfile = buildWindTurbineReferenceProfile(
                referenceShaft,
                speedSignalAnalysis.getAverageRpm()
        );
        List<VibrationModels.SpectrumPeak> observedPeaks = useEnvelope
                ? analyzeEnvelopeSpectrum(vibrationDocumentId, bandHint).getDominantPeaks()
                : analyzeSpectrum(vibrationDocumentId).getDominantPeaks();

        double effectiveToleranceRatio = toleranceRatio > 0 ? toleranceRatio : DEFAULT_MATCH_TOLERANCE_RATIO;
        List<VibrationModels.ReferenceFrequencyMatch> matches = referenceProfile.getEntries().stream()
                .map(entry -> matchReferenceEntry(entry, observedPeaks, referenceProfile.getReferenceFrequencyHz(), effectiveToleranceRatio))
                .toList();

        return VibrationModels.WindTurbineReferenceMatchReport.builder()
                .vibrationDocumentId(vibrationDocumentId)
                .speedDocumentId(speedDocumentId)
                .referenceShaft(referenceProfile.getReferenceShaft())
                .usedEnvelope(useEnvelope)
                .referenceRpm(referenceProfile.getReferenceRpm())
                .toleranceRatio(effectiveToleranceRatio)
                .matches(matches)
                .build();
    }

    @Override
    public VibrationModels.BearingCharacteristicFrequencies calculateBearingCharacteristicFrequencies(
            double shaftFrequencyHz,
            int rollingElementCount,
            double rollingElementDiameterMm,
            double pitchDiameterMm,
            double contactAngleDeg
    ) {
        double ratio = rollingElementDiameterMm / pitchDiameterMm;
        double cosTheta = Math.cos(Math.toRadians(contactAngleDeg));
        double cageFrequencyHz = 0.5 * shaftFrequencyHz * (1 - ratio * cosTheta);
        double ballSpinFrequencyHz = (pitchDiameterMm / (2.0 * rollingElementDiameterMm))
                * shaftFrequencyHz
                * (1 - Math.pow(ratio * cosTheta, 2));
        double outerRaceFrequencyHz = (rollingElementCount / 2.0) * shaftFrequencyHz * (1 - ratio * cosTheta);
        double innerRaceFrequencyHz = (rollingElementCount / 2.0) * shaftFrequencyHz * (1 + ratio * cosTheta);

        return VibrationModels.BearingCharacteristicFrequencies.builder()
                .shaftFrequencyHz(shaftFrequencyHz)
                .rollingElementCount(rollingElementCount)
                .rollingElementDiameterMm(rollingElementDiameterMm)
                .pitchDiameterMm(pitchDiameterMm)
                .contactAngleDeg(contactAngleDeg)
                .cageFrequencyHz(cageFrequencyHz)
                .ballSpinFrequencyHz(ballSpinFrequencyHz)
                .outerRaceFrequencyHz(outerRaceFrequencyHz)
                .innerRaceFrequencyHz(innerRaceFrequencyHz)
                .build();
    }

    @Override
    public VibrationModels.DiagnosisResult diagnose(String documentId, String symptomHint) throws IOException {
        return diagnose(documentId, symptomHint, DiagnosisThresholdProfile.defaults());
    }

    @Override
    public VibrationModels.DiagnosisResult diagnose(
            String documentId,
            String symptomHint,
            DiagnosisThresholdProfile thresholdProfile
    ) throws IOException {
        VibrationModels.SpectrumAnalysis spectrumAnalysis = analyzeSpectrum(documentId);
        List<VibrationModels.DiagnosisCandidate> candidates = buildDiagnosisCandidates(spectrumAnalysis, thresholdProfile);
        if (candidates.isEmpty()) {
            candidates = List.of(VibrationModels.DiagnosisCandidate.builder()
                    .label("特征不明显")
                    .confidence(0.35)
                    .evidence(List.of("当前频谱与统计特征没有形成明显的单一故障模式"))
                    .recommendation("建议结合转速、工况和历史基线进一步判断")
                    .build());
        }

        String summary = buildDiagnosisSummary(spectrumAnalysis, candidates, symptomHint);

        return VibrationModels.DiagnosisResult.builder()
                .documentId(spectrumAnalysis.getDocumentId())
                .filename(spectrumAnalysis.getFilename())
                .deviceName(spectrumAnalysis.getDeviceName())
                .signalName(spectrumAnalysis.getSignalName())
                .sampleRate(spectrumAnalysis.getSampleRate())
                .unit(spectrumAnalysis.getUnit())
                .symptomHint(symptomHint)
                .candidates(candidates)
                .limitations(List.of(
                        "当前诊断基于单通道时域信号与频谱启发式规则，不等同于专业状态监测结论",
                        "未使用转速、载荷、轴承型号等先验信息，无法做精确故障定位",
                        "如果需要更强诊断能力，建议后续补充包络谱、阶次分析和基线对比"
                ))
                .summary(summary)
                .analyzedAt(LocalDateTime.now())
                .build();
    }

    private DocumentDTO safeToDTO(Document document) {
        try {
            return documentConverter.toDTO(document);
        } catch (JsonProcessingException e) {
            throw new BizException("文档元数据解析失败: " + document.getId());
        }
    }

    private boolean isReadyMatDocument(DocumentDTO dto) {
        DocumentDTO.MetaData metadata = dto.getMetadata();
        return metadata != null
                && DOCUMENT_KIND_VIBRATION_MAT.equals(metadata.getDocumentKind())
                && PROCESSING_STATUS_READY.equals(metadata.getProcessingStatus())
                && metadata.getVibration() != null
                && metadata.getFilePath() != null;
    }

    private VibrationModels.DocumentSummary toDocumentSummary(DocumentDTO dto) {
        DocumentDTO.MetaData metadata = dto.getMetadata();
        DocumentDTO.VibrationMeta vibrationMeta = metadata.getVibration();
        String signalRole = inferSignalRole(dto, vibrationMeta);
        return VibrationModels.DocumentSummary.builder()
                .documentId(dto.getId())
                .kbId(dto.getKbId())
                .filename(dto.getFilename())
                .deviceName(vibrationMeta.getDeviceName())
                .signalName(vibrationMeta.getSignalName())
                .sampleRate(vibrationMeta.getSampleRate())
                .unit(vibrationMeta.getUnit())
                .signalRole(signalRole)
                .roleReason(buildSignalRoleReason(signalRole, vibrationMeta))
                .referenceShaftHint(inferReferenceShaftHint(dto, vibrationMeta, signalRole))
                .hasVibrationSignal(vibrationMeta.getHasVibrationSignal())
                .hasSpeedSignal(vibrationMeta.getHasSpeedSignal())
                .defaultSpeedSignalName(vibrationMeta.getDefaultSpeedSignalName())
                .sampleCount(vibrationMeta.getSampleCount())
                .durationSeconds(vibrationMeta.getDurationSeconds())
                .processingStatus(metadata.getProcessingStatus())
                .build();
    }

    private String inferSignalRole(DocumentDTO dto, DocumentDTO.VibrationMeta vibrationMeta) {
        if (vibrationMeta != null
                && Boolean.TRUE.equals(vibrationMeta.getHasSpeedSignal())
                && Boolean.TRUE.equals(vibrationMeta.getHasVibrationSignal())) {
            return "MIXED";
        }
        String unit = vibrationMeta != null ? safeLower(vibrationMeta.getUnit()) : "";
        String signalName = vibrationMeta != null ? safeLower(vibrationMeta.getSignalName()) : "";
        String deviceName = vibrationMeta != null ? safeLower(vibrationMeta.getDeviceName()) : "";
        String filename = safeLower(dto != null ? dto.getFilename() : null);
        String joined = String.join(" ", signalName, deviceName, filename);

        boolean looksLikeSpeed = unit.contains("rpm")
                || unit.contains("hz")
                || unit.contains("rps")
                || joined.contains("speed")
                || joined.contains("rpm")
                || joined.contains("tach")
                || joined.contains("rot")
                || joined.contains("shaft speed")
                || joined.contains("转速")
                || joined.contains("转频");
        if (looksLikeSpeed) {
            return "SPEED";
        }

        boolean looksLikeVibration = unit.contains("m/s^2")
                || unit.contains("m/s2")
                || unit.contains("mm/s")
                || unit.contains("ips")
                || unit.contains("g")
                || joined.contains("vib")
                || joined.contains("acc")
                || joined.contains("accel")
                || joined.contains("振动")
                || joined.contains("加速度")
                || joined.contains("速度振动");
        if (looksLikeVibration) {
            return "VIBRATION";
        }

        return "UNKNOWN";
    }

    private String buildSignalRoleReason(String signalRole, DocumentDTO.VibrationMeta vibrationMeta) {
        if ("MIXED".equals(signalRole)) {
            return "This MAT file contains both vibration-like channels and a speed-like channel.";
        }
        if ("SPEED".equals(signalRole)) {
            return "Inferred from unit or signal name keywords such as rpm, Hz, tach, or speed.";
        }
        if ("VIBRATION".equals(signalRole)) {
            return "Inferred from vibration-related units or signal name keywords such as g, mm/s, or acceleration.";
        }
        return "No strong speed or vibration hint was found in the parsed MAT metadata.";
    }

    private String inferReferenceShaftHint(
            DocumentDTO dto,
            DocumentDTO.VibrationMeta vibrationMeta,
            String signalRole
    ) {
        if (!"SPEED".equals(signalRole) && !"MIXED".equals(signalRole)) {
            return null;
        }

        String joined = String.join(
                " ",
                safeLower(dto != null ? dto.getFilename() : null),
                safeLower(vibrationMeta != null ? vibrationMeta.getSignalName() : null),
                safeLower(vibrationMeta != null ? vibrationMeta.getDeviceName() : null)
        );

        if (joined.contains("hss")
                || joined.contains("high speed")
                || joined.contains("generator")
                || joined.contains("高速")) {
            return REFERENCE_SHAFT_HIGH;
        }
        if (joined.contains("main")
                || joined.contains("ms")
                || joined.contains("rotor")
                || joined.contains("low speed")
                || joined.contains("主轴")
                || joined.contains("低速")) {
            return REFERENCE_SHAFT_MAIN;
        }
        return null;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private ResolvedDocument loadResolvedDocument(String documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BizException("振动文档不存在: " + documentId);
        }
        DocumentDTO dto = safeToDTO(document);
        if (!isReadyMatDocument(dto)) {
            throw new BizException("该文档不是可分析的 MAT 振动文件: " + dto.getFilename());
        }
        return new ResolvedDocument(dto, documentStorageService.getFilePath(dto.getMetadata().getFilePath()));
    }

    private VibrationModels.SignalData loadVibrationSignal(Path path) throws IOException {
        return matFileParserService.parseVibration(path);
    }

    private VibrationModels.SignalData loadSpeedSignal(Path path) throws IOException {
        return matFileParserService.parseSpeed(path);
    }

    private DocumentDTO.BasicStats calculateBasicStats(double[] samples) {
        double mean = 0.0;
        double energy = 0.0;
        double peak = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (double sample : samples) {
            mean += sample;
            energy += sample * sample;
            peak = Math.max(peak, Math.abs(sample));
            min = Math.min(min, sample);
            max = Math.max(max, sample);
        }
        mean /= samples.length;
        double rms = Math.sqrt(energy / samples.length);

        double varianceAcc = 0.0;
        double fourthMomentAcc = 0.0;
        for (double sample : samples) {
            double centered = sample - mean;
            double squared = centered * centered;
            varianceAcc += squared;
            fourthMomentAcc += squared * squared;
        }
        double variance = varianceAcc / samples.length;
        double standardDeviation = Math.sqrt(variance);
        double kurtosis = variance == 0.0
                ? 0.0
                : (fourthMomentAcc / samples.length) / (variance * variance);

        DocumentDTO.BasicStats basicStats = new DocumentDTO.BasicStats();
        basicStats.setMean(mean);
        basicStats.setRms(rms);
        basicStats.setStandardDeviation(standardDeviation);
        basicStats.setPeakAbs(peak);
        basicStats.setPeakToPeak(max - min);
        basicStats.setCrestFactor(rms == 0.0 ? 0.0 : peak / rms);
        basicStats.setKurtosis(kurtosis);
        return basicStats;
    }

    private SpectrumComputation computeSpectrum(double[] samples, double sampleRate) {
        int sourceLength = Math.min(samples.length, MAX_FFT_SAMPLES);
        int fftSize = nextPowerOfTwo(sourceLength);
        double[] windowed = new double[fftSize];

        double mean = 0.0;
        for (int i = 0; i < sourceLength; i++) {
            mean += samples[i];
        }
        mean /= sourceLength;

        for (int i = 0; i < sourceLength; i++) {
            double window = sourceLength == 1
                    ? 1.0
                    : 0.5 - 0.5 * Math.cos((2.0 * Math.PI * i) / (sourceLength - 1));
            windowed[i] = (samples[i] - mean) * window;
        }

        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] transformed = transformer.transform(windowed, TransformType.FORWARD);

        int half = transformed.length / 2;
        double[] amplitudes = new double[half];
        List<VibrationModels.SpectrumPoint> preview = new ArrayList<>(half);
        for (int i = 0; i < half; i++) {
            double amplitude = transformed[i].abs() * 2.0 / sourceLength;
            if (i == 0) {
                amplitude /= 2.0;
            }
            amplitudes[i] = amplitude;
            preview.add(VibrationModels.SpectrumPoint.builder()
                    .frequencyHz((i * sampleRate) / fftSize)
                    .amplitude(amplitude)
                    .build());
        }

        return new SpectrumComputation(findDominantPeaks(amplitudes, sampleRate, fftSize), downSample(preview));
    }

    private double[] buildEnvelope(double[] samples, double sampleRate, String bandHint) {
        int sourceLength = Math.min(samples.length, MAX_FFT_SAMPLES);
        int fftSize = nextPowerOfTwo(sourceLength);
        double[] prepared = new double[fftSize];
        double mean = 0.0;
        for (int i = 0; i < sourceLength; i++) {
            mean += samples[i];
        }
        mean /= sourceLength;

        for (int i = 0; i < sourceLength; i++) {
            prepared[i] = samples[i] - mean;
        }

        FastFourierTransformer transformer = new FastFourierTransformer(DftNormalization.STANDARD);
        Complex[] transformed = transformer.transform(prepared, TransformType.FORWARD);
        Complex[] filtered = applyBandHint(transformed, sampleRate, fftSize, bandHint);
        Complex[] analyticSpectrum = buildAnalyticSpectrum(filtered);
        Complex[] analyticSignal = transformer.transform(analyticSpectrum, TransformType.INVERSE);

        double[] envelope = new double[sourceLength];
        for (int i = 0; i < sourceLength; i++) {
            envelope[i] = analyticSignal[i].abs();
        }
        return envelope;
    }

    private Complex[] applyBandHint(Complex[] transformed, double sampleRate, int fftSize, String bandHint) {
        FrequencyBand band = parseBandHint(bandHint);
        if (band == null) {
            return transformed;
        }

        Complex[] filtered = new Complex[transformed.length];
        for (int i = 0; i < transformed.length; i++) {
            double frequencyHz = i <= fftSize / 2
                    ? (i * sampleRate) / fftSize
                    : ((fftSize - i) * sampleRate) / fftSize;
            filtered[i] = band.contains(frequencyHz) ? transformed[i] : Complex.ZERO;
        }
        return filtered;
    }

    private Complex[] buildAnalyticSpectrum(Complex[] spectrum) {
        int n = spectrum.length;
        Complex[] analytic = new Complex[n];
        for (int i = 0; i < n; i++) {
            analytic[i] = Complex.ZERO;
        }

        analytic[0] = spectrum[0];
        int half = n / 2;
        if (n % 2 == 0) {
            analytic[half] = spectrum[half];
        }

        for (int i = 1; i < half; i++) {
            analytic[i] = spectrum[i].multiply(2.0);
        }
        return analytic;
    }

    private List<VibrationModels.SpectrumPeak> findDominantPeaks(double[] amplitudes, double sampleRate, int fftSize) {
        List<VibrationModels.SpectrumPeak> peaks = new ArrayList<>();
        for (int i = 1; i < amplitudes.length - 1; i++) {
            double current = amplitudes[i];
            if (current <= amplitudes[i - 1] || current < amplitudes[i + 1]) {
                continue;
            }
            peaks.add(VibrationModels.SpectrumPeak.builder()
                    .binIndex(i)
                    .frequencyHz((i * sampleRate) / fftSize)
                    .amplitude(current)
                    .build());
        }

        if (peaks.isEmpty()) {
            for (int i = 1; i < amplitudes.length; i++) {
                peaks.add(VibrationModels.SpectrumPeak.builder()
                        .binIndex(i)
                        .frequencyHz((i * sampleRate) / fftSize)
                        .amplitude(amplitudes[i])
                        .build());
            }
        }

        return peaks.stream()
                .sorted(Comparator.comparingDouble(VibrationModels.SpectrumPeak::getAmplitude).reversed())
                .limit(MAX_DOMINANT_PEAKS)
                .toList();
    }

    private List<VibrationModels.SpectrumPoint> downSample(List<VibrationModels.SpectrumPoint> spectrum) {
        if (spectrum.size() <= MAX_PREVIEW_POINTS) {
            return spectrum;
        }

        List<VibrationModels.SpectrumPoint> preview = new ArrayList<>(MAX_PREVIEW_POINTS);
        double step = (double) (spectrum.size() - 1) / (MAX_PREVIEW_POINTS - 1);
        for (int i = 0; i < MAX_PREVIEW_POINTS; i++) {
            int index = (int) Math.round(i * step);
            preview.add(spectrum.get(Math.min(index, spectrum.size() - 1)));
        }
        return preview;
    }

    private List<VibrationModels.DiagnosisCandidate> buildDiagnosisCandidates(
            VibrationModels.SpectrumAnalysis analysis,
            DiagnosisThresholdProfile thresholdProfile
    ) {
        List<VibrationModels.DiagnosisCandidate> candidates = new ArrayList<>();
        List<VibrationModels.SpectrumPeak> peaks = analysis.getDominantPeaks();
        if (peaks == null || peaks.isEmpty()) {
            return candidates;
        }

        DiagnosisThresholdProfile effectiveProfile = thresholdProfile != null
                ? thresholdProfile
                : DiagnosisThresholdProfile.defaults();

        VibrationModels.SpectrumPeak dominantPeak = peaks.get(0);
        double dominantFrequency = dominantPeak.getFrequencyHz();
        double amplitudeSum = peaks.stream().mapToDouble(VibrationModels.SpectrumPeak::getAmplitude).sum();
        double dominantRatio = amplitudeSum == 0.0 ? 0.0 : dominantPeak.getAmplitude() / amplitudeSum;
        int harmonicCount = countHarmonics(peaks, dominantFrequency);
        DocumentDTO.BasicStats stats = analysis.getBasicStats();

        if (dominantRatio >= 0.55 && harmonicCount <= 1) {
            candidates.add(VibrationModels.DiagnosisCandidate.builder()
                    .label("疑似不平衡或偏心")
                    .confidence(roundConfidence(Math.min(0.88, 0.62 + dominantRatio * 0.3)))
                    .evidence(List.of(
                            String.format("主频 %.2f Hz 幅值最强，占主要峰值能量 %.1f%%", dominantFrequency, dominantRatio * 100.0),
                            "谐波数量较少，表现为明显单一主频主导"
                    ))
                    .recommendation("建议优先检查转子配重、偏心以及安装同轴性")
                    .build());
        }

        if (harmonicCount >= 2) {
            candidates.add(VibrationModels.DiagnosisCandidate.builder()
                    .label("疑似不对中或机械松动")
                    .confidence(roundConfidence(Math.min(0.85, 0.58 + harmonicCount * 0.08)))
                    .evidence(List.of(
                            String.format("检测到 %d 个与主频相关的明显谐波", harmonicCount),
                            "多阶谐波通常意味着结构松动、联轴器不对中或装配问题"
                    ))
                    .recommendation("建议检查联轴器对中、基础紧固和安装刚度")
                    .build());
        }

        if (stats != null && stats.getCrestFactor() != null && stats.getKurtosis() != null
                && (stats.getCrestFactor() >= effectiveProfile.getCrestFactorWarn()
                || stats.getKurtosis() >= effectiveProfile.getKurtosisWarn())) {
            double crestFactorExcess = stats.getCrestFactor() - effectiveProfile.getCrestFactorWarn();
            candidates.add(VibrationModels.DiagnosisCandidate.builder()
                    .label("疑似冲击性异常")
                    .confidence(roundConfidence(0.66 + Math.min(0.18, Math.max(0.0, crestFactorExcess) * 0.04)))
                    .evidence(List.of(
                            String.format("波形峰值因子为 %.2f", stats.getCrestFactor()),
                            String.format("峰度为 %.2f，存在明显尖峰/冲击特征", stats.getKurtosis())
                    ))
                    .recommendation("建议进一步做包络谱分析，重点排查轴承局部损伤与松动冲击")
                    .build());
        }

        double highFreqRatio = estimateHighFrequencyRatio(analysis.getPreviewSpectrum(), analysis.getSampleRate());
        if (highFreqRatio >= effectiveProfile.getHighFrequencyEnergyRatioWarn()) {
            candidates.add(VibrationModels.DiagnosisCandidate.builder()
                    .label("疑似高频摩擦或早期磨损")
                    .confidence(roundConfidence(Math.min(0.76, 0.55 + highFreqRatio * 0.25)))
                    .evidence(List.of(
                            String.format("高频能量占比约 %.1f%%", highFreqRatio * 100.0),
                            "高频宽带能量偏高，常见于摩擦、磨损或早期轴承异常"
                    ))
                    .recommendation("建议结合润滑状态、温升和包络谱结果进一步确认")
                    .build());
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(VibrationModels.DiagnosisCandidate::getConfidence).reversed())
                .toList();
    }

    private int countHarmonics(List<VibrationModels.SpectrumPeak> peaks, double fundamentalFrequency) {
        if (fundamentalFrequency <= 0) {
            return 0;
        }

        int harmonicCount = 0;
        for (VibrationModels.SpectrumPeak peak : peaks) {
            if (peak.getFrequencyHz() == fundamentalFrequency) {
                continue;
            }
            double ratio = peak.getFrequencyHz() / fundamentalFrequency;
            double nearestInteger = Math.rint(ratio);
            if (nearestInteger >= 2.0 && Math.abs(ratio - nearestInteger) <= 0.08) {
                harmonicCount++;
            }
        }
        return harmonicCount;
    }

    private double estimateHighFrequencyRatio(List<VibrationModels.SpectrumPoint> spectrum, double sampleRate) {
        if (spectrum == null || spectrum.isEmpty()) {
            return 0.0;
        }

        double splitFrequency = sampleRate * 0.2;
        double total = 0.0;
        double high = 0.0;
        for (VibrationModels.SpectrumPoint point : spectrum) {
            total += point.getAmplitude();
            if (point.getFrequencyHz() >= splitFrequency) {
                high += point.getAmplitude();
            }
        }
        return total == 0.0 ? 0.0 : high / total;
    }

    private double[] normalizeSpeedSamples(double[] samples, String unit) {
        double[] normalized = new double[samples.length];
        String normalizedUnit = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        boolean isHz = normalizedUnit.contains("hz") || normalizedUnit.contains("1/s") || normalizedUnit.contains("rps");

        for (int i = 0; i < samples.length; i++) {
            double value = Math.abs(samples[i]);
            normalized[i] = isHz ? value * 60.0 : value;
        }
        return normalized;
    }

    private SpeedStats computeSpeedStats(double[] rpmSamples) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (double rpm : rpmSamples) {
            min = Math.min(min, rpm);
            max = Math.max(max, rpm);
            sum += rpm;
        }
        double average = rpmSamples.length == 0 ? 0.0 : sum / rpmSamples.length;

        double varianceAcc = 0.0;
        for (double rpm : rpmSamples) {
            double delta = rpm - average;
            varianceAcc += delta * delta;
        }
        double std = rpmSamples.length == 0 ? 0.0 : Math.sqrt(varianceAcc / rpmSamples.length);

        return new SpeedStats(average, min == Double.POSITIVE_INFINITY ? 0.0 : min, max == Double.NEGATIVE_INFINITY ? 0.0 : max, std);
    }

    private List<Double> downSampleSeries(double[] values) {
        if (values.length <= MAX_PREVIEW_POINTS) {
            List<Double> preview = new ArrayList<>(values.length);
            for (double value : values) {
                preview.add(value);
            }
            return preview;
        }

        List<Double> preview = new ArrayList<>(MAX_PREVIEW_POINTS);
        double step = (double) (values.length - 1) / (MAX_PREVIEW_POINTS - 1);
        for (int i = 0; i < MAX_PREVIEW_POINTS; i++) {
            int index = (int) Math.round(i * step);
            preview.add(values[Math.min(index, values.length - 1)]);
        }
        return preview;
    }

    private String normalizeReferenceShaft(String referenceShaft) {
        if (referenceShaft == null || referenceShaft.isBlank()) {
            return REFERENCE_SHAFT_HIGH;
        }
        String normalized = referenceShaft.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals(REFERENCE_SHAFT_MAIN) || normalized.equals("MAIN") || normalized.equals("MAIN_SHAFT")) {
            return REFERENCE_SHAFT_MAIN;
        }
        return REFERENCE_SHAFT_HIGH;
    }

    private double resolveReferenceFrequencyHz(String referenceShaft, VibrationModels.SpeedSignalAnalysis speedSignalAnalysis) {
        return switch (referenceShaft) {
            case REFERENCE_SHAFT_MAIN -> speedSignalAnalysis.getEquivalentFrequencyHz();
            case REFERENCE_SHAFT_HIGH -> speedSignalAnalysis.getEquivalentFrequencyHz();
            default -> speedSignalAnalysis.getEquivalentFrequencyHz();
        };
    }

    private VibrationModels.ReferenceFrequencyEntry materializeReference(
            VibrationModels.ReferenceFrequencyEntry normalizedEntry,
            String referenceShaft,
            double referenceFrequencyHz
    ) {
        Double relative = REFERENCE_SHAFT_MAIN.equals(referenceShaft)
                ? normalizedEntry.getRelativeToMainShaft()
                : normalizedEntry.getRelativeToHighSpeedShaft();
        if (relative == null) {
            return null;
        }

        return VibrationModels.ReferenceFrequencyEntry.builder()
                .category(normalizedEntry.getCategory())
                .component(normalizedEntry.getComponent())
                .feature(normalizedEntry.getFeature())
                .relativeToMainShaft(normalizedEntry.getRelativeToMainShaft())
                .relativeToHighSpeedShaft(normalizedEntry.getRelativeToHighSpeedShaft())
                .expectedFrequencyHz(relative * referenceFrequencyHz)
                .build();
    }

    VibrationModels.ReferenceFrequencyMatch matchReferenceEntry(
            VibrationModels.ReferenceFrequencyEntry referenceEntry,
            List<VibrationModels.SpectrumPeak> observedPeaks,
            double referenceFrequencyHz,
            double toleranceRatio
    ) {
        if (observedPeaks == null) {
            observedPeaks = List.of();
        }

        VibrationModels.SpectrumPeak nearestPeak = null;
        double smallestErrorRatio = Double.POSITIVE_INFINITY;
        double smallestFrequencyErrorHz = Double.POSITIVE_INFINITY;

        for (VibrationModels.SpectrumPeak observedPeak : observedPeaks) {
            double frequencyErrorHz = Math.abs(observedPeak.getFrequencyHz() - referenceEntry.getExpectedFrequencyHz());
            double errorRatio = frequencyErrorHz / Math.max(referenceEntry.getExpectedFrequencyHz(), 1e-9);
            if (errorRatio < smallestErrorRatio) {
                smallestErrorRatio = errorRatio;
                smallestFrequencyErrorHz = frequencyErrorHz;
                nearestPeak = observedPeak;
            }
        }

        Double observedOrder = nearestPeak != null && referenceFrequencyHz > 0
                ? nearestPeak.getFrequencyHz() / referenceFrequencyHz
                : null;
        Double orderError = referenceFrequencyHz > 0 && nearestPeak != null
                ? smallestFrequencyErrorHz / referenceFrequencyHz
                : null;

        boolean relativeMatched = nearestPeak != null && smallestErrorRatio <= toleranceRatio;
        boolean orderMatched = orderError != null && orderError <= DEFAULT_MATCH_ORDER_TOLERANCE;
        boolean matched = relativeMatched || orderMatched;

        return VibrationModels.ReferenceFrequencyMatch.builder()
                .category(referenceEntry.getCategory())
                .component(referenceEntry.getComponent())
                .feature(referenceEntry.getFeature())
                .expectedFrequencyHz(referenceEntry.getExpectedFrequencyHz())
                .observedFrequencyHz(nearestPeak != null ? nearestPeak.getFrequencyHz() : null)
                .observedAmplitude(nearestPeak != null ? nearestPeak.getAmplitude() : null)
                .observedOrder(observedOrder)
                .frequencyErrorHz(nearestPeak != null ? smallestFrequencyErrorHz : null)
                .errorRatio(nearestPeak != null ? smallestErrorRatio : null)
                .orderError(orderError)
                .matchBasis(resolveMatchBasis(relativeMatched, orderMatched, nearestPeak != null))
                .matched(matched)
                .build();
    }

    private String resolveMatchBasis(boolean relativeMatched, boolean orderMatched, boolean hasNearestPeak) {
        if (relativeMatched && orderMatched) {
            return "relative_frequency_and_order_tolerance";
        }
        if (relativeMatched) {
            return "relative_frequency";
        }
        if (orderMatched) {
            return "order_tolerance";
        }
        return hasNearestPeak ? "nearest_peak_only" : "no_peak";
    }

    private List<VibrationModels.ReferenceFrequencyEntry> buildReferenceCatalog() {
        return List.of(
                reference("shaft", "rotor/carrier", "shaft frequency", 1.00, 0.012),
                reference("shaft", "planets minus carrier", "shaft frequency", 2.54, 0.031),
                reference("shaft", "planets", "shaft frequency", 3.54, 0.043),
                reference("shaft", "sun shaft minus carrier", "shaft frequency", 4.71, 0.058),
                reference("shaft", "sun shaft", "shaft frequency", 5.71, 0.070),
                reference("shaft", "intermediate shaft", "shaft frequency", 20.37, 0.250),
                reference("shaft", "generator shaft", "shaft frequency", 81.49, 1.000),

                reference("gear_mesh", "planet to ring", "mesh frequency", 99.00, 1.215),
                reference("gear_mesh", "sun to planet", "mesh frequency", 99.00, 1.215),
                reference("gear_mesh", "sun shaft to intermediate", "mesh frequency", 468.57, 5.750),
                reference("gear_mesh", "intermediate to hss", "mesh frequency", 1792.80, 22.000),

                reference("gear_tooth", "hss gear set pinion", "single tooth fault", 81.49, 1.000),
                reference("gear_tooth", "hss gear set wheel", "single tooth fault", 20.37, 0.250),
                reference("gear_tooth", "intermediate gear set pinion", "single tooth fault", 20.37, 0.250),
                reference("gear_tooth", "intermediate gear set wheel", "single tooth fault", 5.71, 0.070),
                reference("gear_tooth", "sun gear", "single tooth fault", 14.14, 0.174),
                reference("gear_tooth", "planet gear", "single tooth fault", 7.08, 0.087),
                reference("gear_tooth", "ring gear", "single tooth fault", 3.00, 0.037),

                reference("bearing", "main bearing INP-A", "roller rotation", 5.22, 0.064),
                reference("bearing", "main bearing INP-A", "cage", 0.45, 0.006),
                reference("bearing", "main bearing INP-A", "roller defect", 10.44, 0.128),
                reference("bearing", "main bearing INP-A", "outer race", 12.70, 0.156),
                reference("bearing", "main bearing INP-A", "inner race", 15.30, 0.188),

                reference("bearing", "hss A1&A2", "roller rotation", 253.99, 3.117),
                reference("bearing", "hss A1&A2", "cage", 34.63, 0.425),
                reference("bearing", "hss A1&A2", "roller defect", 507.98, 6.234),
                reference("bearing", "hss A1&A2", "outer race", 691.89, 8.490),
                reference("bearing", "hss A1&A2", "inner race", 937.93, 11.510),

                reference("bearing", "hss B", "roller rotation", 254.78, 3.127),
                reference("bearing", "hss B", "cage", 47.10, 0.578),
                reference("bearing", "hss B", "roller defect", 509.56, 6.253),
                reference("bearing", "hss B", "outer race", 584.60, 7.174),
                reference("bearing", "hss B", "inner race", 800.74, 9.826),

                reference("bearing", "iss C1&C2", "roller rotation", 106.14, 1.303),
                reference("bearing", "iss C1&C2", "cage", 9.25, 0.114),
                reference("bearing", "iss C1&C2", "roller defect", 211.88, 2.600),
                reference("bearing", "iss C1&C2", "outer race", 287.25, 3.525),

                reference("bearing", "iss D", "roller rotation", 63.70, 0.782),
                reference("bearing", "iss D", "cage", 11.78, 0.145),
                reference("bearing", "iss D", "roller defect", 127.39, 1.563),
                reference("bearing", "iss D", "outer race", 146.15, 1.793),
                reference("bearing", "iss D", "inner race", 200.19, 2.457),

                reference("bearing", "lss E1&E2", "roller rotation", 41.37, 0.508),
                reference("bearing", "lss E1&E2", "cage", 2.67, 0.033),
                reference("bearing", "lss E1&E2", "roller defect", 82.86, 1.017),
                reference("bearing", "lss E1&E2", "outer race", 109.71, 1.346),
                reference("bearing", "lss E1&E2", "inner race", 124.57, 1.529),

                reference("bearing", "hollow shaft F", "roller rotation", 47.20, 0.579),
                reference("bearing", "hollow shaft F", "cage", 3.03, 0.037),
                reference("bearing", "hollow shaft F", "roller defect", 94.39, 1.158),
                reference("bearing", "hollow shaft F", "outer race", 139.61, 1.713),
                reference("bearing", "hollow shaft F", "inner race", 157.53, 1.933),

                reference("bearing", "carrier G", "roller rotation", 8.59, 0.105),
                reference("bearing", "carrier G", "cage", 0.53, 0.006),
                reference("bearing", "carrier G", "roller defect", 17.17, 0.211),
                reference("bearing", "carrier G", "outer race", 25.43, 0.312),
                reference("bearing", "carrier G", "inner race", 28.57, 0.351),

                reference("bearing", "carrier H", "roller rotation", 8.11, 0.100),
                reference("bearing", "carrier H", "cage", 0.53, 0.007),
                reference("bearing", "carrier H", "roller defect", 16.22, 0.199),
                reference("bearing", "carrier H", "outer race", 23.93, 0.294)
        );
    }

    private VibrationModels.ReferenceFrequencyEntry reference(
            String category,
            String component,
            String feature,
            Double relativeToMainShaft,
            Double relativeToHighSpeedShaft
    ) {
        return VibrationModels.ReferenceFrequencyEntry.builder()
                .category(category)
                .component(component)
                .feature(feature)
                .relativeToMainShaft(relativeToMainShaft)
                .relativeToHighSpeedShaft(relativeToHighSpeedShaft)
                .build();
    }

    private String buildDiagnosisSummary(
            VibrationModels.SpectrumAnalysis analysis,
            List<VibrationModels.DiagnosisCandidate> candidates,
            String symptomHint
    ) {
        VibrationModels.DiagnosisCandidate topCandidate = candidates.get(0);
        String hintSegment = (symptomHint == null || symptomHint.isBlank())
                ? ""
                : "结合补充症状“" + symptomHint.trim() + "”，";
        return String.format(
                "设备 %s 的振动信号 %s 显示主导异常模式为“%s”。%s建议先依据工具给出的证据检查对应机械部位，并保留原始频谱做进一步复核。",
                analysis.getDeviceName(),
                analysis.getSignalName(),
                topCandidate.getLabel(),
                hintSegment
        );
    }

    private double roundConfidence(double confidence) {
        return Math.round(Math.max(0.0, Math.min(confidence, 0.99)) * 100.0) / 100.0;
    }

    private int nextPowerOfTwo(int value) {
        int n = 1;
        while (n < value) {
            n <<= 1;
        }
        return n;
    }

    private FrequencyBand parseBandHint(String bandHint) {
        String normalized = normalizeBandHint(bandHint);
        if (normalized == null) {
            return null;
        }

        String[] parts = normalized.split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            double low = Double.parseDouble(parts[0]);
            double high = Double.parseDouble(parts[1]);
            if (low < 0 || high <= low) {
                return null;
            }
            return new FrequencyBand(low, high);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeBandHint(String bandHint) {
        if (bandHint == null || bandHint.isBlank()) {
            return null;
        }
        String normalized = bandHint.trim()
                .replace("Hz", "")
                .replace("hz", "")
                .replace("~", "-")
                .replace("—", "-")
                .replace("–", "-")
                .replace(" ", "");
        return normalized.isBlank() ? null : normalized;
    }

    private record ResolvedDocument(DocumentDTO document, Path path) {
    }

    private record SpectrumComputation(
            List<VibrationModels.SpectrumPeak> peaks,
            List<VibrationModels.SpectrumPoint> preview
    ) {
    }

    private record SpeedStats(
            double averageRpm,
            double minRpm,
            double maxRpm,
            double stdRpm
    ) {
    }

    private record FrequencyBand(double lowHz, double highHz) {
        private boolean contains(double frequencyHz) {
            return frequencyHz >= lowHz && frequencyHz <= highHz;
        }
    }
}
