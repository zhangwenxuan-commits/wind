package com.kama.jchatmind.service.impl;

import com.jmatio.io.MatFileReader;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLChar;
import com.jmatio.types.MLNumericArray;
import com.kama.jchatmind.exception.BizException;
import com.kama.jchatmind.service.MatFileParserService;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MatFileParserServiceImpl implements MatFileParserService {

    private static final String SIGNAL_FIELD = "signal";
    private static final double DEFAULT_FIXED_SAMPLE_RATE = 40000.0;
    private static final List<String> SAMPLE_RATE_ALIASES = List.of(
            "sampleRate", "sample_rate", "SampleRate", "fs", "Fs", "FS", "samplingRate", "sampling_rate", "samplingFrequency"
    );
    private static final List<String> UNIT_ALIASES = List.of(
            "unit", "units", "signalUnit", "signal_unit"
    );
    private static final List<String> DEVICE_NAME_ALIASES = List.of(
            "deviceName", "device_name", "sensorName", "sensor_name", "channelName", "channel_name"
    );
    private static final List<String> TIME_ALIASES = List.of(
            "time", "Time", "t", "timestamp", "timestamps"
    );

    @Override
    public VibrationModels.SignalData parse(Path path) throws IOException {
        return parseVibration(path);
    }

    @Override
    public VibrationModels.SignalData parseVibration(Path path) throws IOException {
        return parseInternal(path, SignalPreference.VIBRATION);
    }

    @Override
    public VibrationModels.SignalData parseSpeed(Path path) throws IOException {
        return parseInternal(path, SignalPreference.SPEED);
    }

    @Override
    public VibrationModels.MatSignalCatalog inspect(Path path) throws IOException {
        MatFileReader reader = new MatFileReader(path.toFile());
        Map<String, MLArray> content = reader.getContent();

        if (content.containsKey(SIGNAL_FIELD)) {
            VibrationModels.SignalData legacySignal = parseLegacy(content, path);
            return VibrationModels.MatSignalCatalog.builder()
                    .signalNames(List.of(legacySignal.getSignalName()))
                    .defaultVibrationSignalName(legacySignal.getSignalName())
                    .defaultSpeedSignalName(isSpeedLike(legacySignal.getSignalName()) ? legacySignal.getSignalName() : null)
                    .sampleRate(legacySignal.getSampleRate())
                    .unit(legacySignal.getUnit())
                    .deviceName(legacySignal.getDeviceName())
                    .hasVibrationSignal(true)
                    .hasSpeedSignal(isSpeedLike(legacySignal.getSignalName()))
                    .build();
        }

        List<SignalCandidate> candidates = listSignalCandidates(content);
        if (candidates.isEmpty()) {
            throw new BizException("MAT file does not contain any top-level vector signal.");
        }

        SignalCandidate vibrationCandidate = selectCandidate(candidates, SignalPreference.VIBRATION, false);
        SignalCandidate speedCandidate = selectCandidate(candidates, SignalPreference.SPEED, false);
        int referenceLength = vibrationCandidate != null
                ? vibrationCandidate.length()
                : candidates.get(0).length();

        return VibrationModels.MatSignalCatalog.builder()
                .signalNames(candidates.stream().map(SignalCandidate::name).toList())
                .defaultVibrationSignalName(vibrationCandidate != null ? vibrationCandidate.name() : null)
                .defaultSpeedSignalName(speedCandidate != null ? speedCandidate.name() : null)
                .sampleRate(resolveSampleRate(content, referenceLength))
                .unit(resolveUnit(content, vibrationCandidate != null ? vibrationCandidate.name() : null, SignalPreference.VIBRATION))
                .deviceName(resolveDeviceName(content, path, vibrationCandidate != null ? vibrationCandidate.name() : null))
                .hasVibrationSignal(vibrationCandidate != null)
                .hasSpeedSignal(speedCandidate != null)
                .build();
    }

    private VibrationModels.SignalData parseInternal(Path path, SignalPreference preference) throws IOException {
        MatFileReader reader = new MatFileReader(path.toFile());
        Map<String, MLArray> content = reader.getContent();

        if (content.containsKey(SIGNAL_FIELD)) {
            return parseLegacy(content, path);
        }

        List<SignalCandidate> candidates = listSignalCandidates(content);
        SignalCandidate selectedCandidate = selectCandidate(candidates, preference, true);
        double[] signal = readVector(selectedCandidate.array(), selectedCandidate.name());
        double sampleRate = resolveSampleRate(content, signal.length);
        String unit = resolveUnit(content, selectedCandidate.name(), preference);
        String deviceName = resolveDeviceName(content, path, selectedCandidate.name());

        return VibrationModels.SignalData.builder()
                .signalName(selectedCandidate.name())
                .samples(signal)
                .sampleRate(sampleRate)
                .unit(unit)
                .deviceName(deviceName)
                .build();
    }

    private VibrationModels.SignalData parseLegacy(Map<String, MLArray> content, Path path) {
        MLArray signalArray = requireArray(content, SIGNAL_FIELD);
        double[] signal = readVector(signalArray, SIGNAL_FIELD);
        double sampleRate = resolveSampleRate(content, signal.length);
        String unit = resolveUnit(content, SIGNAL_FIELD, SignalPreference.VIBRATION);
        String deviceName = resolveDeviceName(content, path, SIGNAL_FIELD);

        if (signal.length == 0) {
            throw new BizException("MAT file signal vector must not be empty.");
        }
        if (sampleRate <= 0) {
            throw new BizException("MAT file sample rate must be greater than zero.");
        }

        return VibrationModels.SignalData.builder()
                .signalName(SIGNAL_FIELD)
                .samples(signal)
                .sampleRate(sampleRate)
                .unit(unit)
                .deviceName(deviceName)
                .build();
    }

    private MLArray requireArray(Map<String, MLArray> content, String fieldName) {
        MLArray array = content.get(fieldName);
        if (array == null) {
            throw new BizException("MAT file is missing field: " + fieldName);
        }
        return array;
    }

    private List<SignalCandidate> listSignalCandidates(Map<String, MLArray> content) {
        List<SignalCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, MLArray> entry : content.entrySet()) {
            String name = entry.getKey();
            MLArray array = entry.getValue();
            if (!(array instanceof MLNumericArray<?> numericArray)) {
                continue;
            }
            if (numericArray.getSize() <= 1) {
                continue;
            }
            if (!isVector(numericArray)) {
                continue;
            }
            if (isMetadataAlias(name)) {
                continue;
            }
            candidates.add(new SignalCandidate(name, array, numericArray.getSize()));
        }

        candidates.sort(Comparator
                .comparingInt(SignalCandidate::length).reversed()
                .thenComparing(SignalCandidate::name));
        return candidates;
    }

    private boolean isVector(MLNumericArray<?> numericArray) {
        return numericArray.getM() == 1 || numericArray.getN() == 1;
    }

    private boolean isMetadataAlias(String fieldName) {
        return matchesAlias(fieldName, SAMPLE_RATE_ALIASES)
                || matchesAlias(fieldName, UNIT_ALIASES)
                || matchesAlias(fieldName, DEVICE_NAME_ALIASES)
                || matchesAlias(fieldName, TIME_ALIASES);
    }

    private SignalCandidate selectCandidate(
            List<SignalCandidate> candidates,
            SignalPreference preference,
            boolean strict
    ) {
        if (candidates == null || candidates.isEmpty()) {
            if (strict) {
                throw new BizException("MAT file does not contain any analyzable vector signal.");
            }
            return null;
        }

        SignalCandidate bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;
        for (SignalCandidate candidate : candidates) {
            int score = scoreCandidate(candidate.name(), preference);
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }

        if (!strict) {
            if (preference == SignalPreference.SPEED && bestScore < 60) {
                return null;
            }
            if (preference == SignalPreference.VIBRATION && bestScore < 10) {
                return null;
            }
            return bestCandidate;
        }

        if (preference == SignalPreference.SPEED && bestScore < 60) {
            throw new BizException("No speed or tachometer signal was identified in the MAT file.");
        }
        return bestCandidate;
    }

    private int scoreCandidate(String signalName, SignalPreference preference) {
        String normalized = normalizeName(signalName);
        boolean speedLike = isSpeedLike(normalized);
        boolean vibrationLike = isVibrationLike(normalized);
        boolean torqueLike = isTorqueLike(normalized);
        boolean timeLike = isTimeLike(normalized);

        if (preference == SignalPreference.SPEED) {
            if (speedLike) {
                return 100;
            }
            if (timeLike || torqueLike) {
                return -20;
            }
            return 5;
        }

        if (vibrationLike) {
            return 100;
        }
        if (speedLike || torqueLike || timeLike) {
            return -20;
        }
        return 30;
    }

    private double resolveSampleRate(Map<String, MLArray> content, int expectedLength) {
        MLArray sampleRateArray = findFirstArray(content, SAMPLE_RATE_ALIASES);
        if (sampleRateArray != null) {
            double sampleRate = readScalar(sampleRateArray, "sampleRate");
            if (sampleRate > 0) {
                return sampleRate;
            }
        }

        MLArray timeArray = findFirstArray(content, TIME_ALIASES);
        if (timeArray != null) {
            double[] timeVector = readVector(timeArray, "time");
            if (timeVector.length == expectedLength) {
                double sampleRate = inferSampleRateFromTime(timeVector);
                if (sampleRate > 0) {
                    return sampleRate;
                }
            }
        }

        return DEFAULT_FIXED_SAMPLE_RATE;
    }

    private double inferSampleRateFromTime(double[] timeVector) {
        if (timeVector.length < 2) {
            return 0.0;
        }

        double deltaSum = 0.0;
        int validCount = 0;
        for (int i = 1; i < timeVector.length; i++) {
            double delta = timeVector[i] - timeVector[i - 1];
            if (delta > 0) {
                deltaSum += delta;
                validCount++;
            }
        }

        if (validCount == 0) {
            return 0.0;
        }
        double averageDelta = deltaSum / validCount;
        return averageDelta > 0 ? 1.0 / averageDelta : 0.0;
    }

    private String resolveUnit(Map<String, MLArray> content, String signalName, SignalPreference preference) {
        MLArray unitArray = findFirstArray(content, UNIT_ALIASES);
        if (unitArray != null) {
            String unit = readString(unitArray, "unit");
            if (!unit.isBlank()) {
                return unit;
            }
        }

        String normalized = normalizeName(signalName);
        if (preference == SignalPreference.SPEED || isSpeedLike(normalized)) {
            return "rpm";
        }
        return "unknown";
    }

    private String resolveDeviceName(Map<String, MLArray> content, Path path, String signalName) {
        MLArray deviceNameArray = findFirstArray(content, DEVICE_NAME_ALIASES);
        if (deviceNameArray != null) {
            String deviceName = readString(deviceNameArray, "deviceName");
            if (!deviceName.isBlank()) {
                return deviceName;
            }
        }
        return fileStem(path) + (signalName == null ? "" : ":" + signalName);
    }

    private MLArray findFirstArray(Map<String, MLArray> content, List<String> aliases) {
        for (String alias : aliases) {
            MLArray array = content.get(alias);
            if (array != null) {
                return array;
            }
        }
        for (Map.Entry<String, MLArray> entry : content.entrySet()) {
            if (matchesAlias(entry.getKey(), aliases)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesAlias(String actualName, List<String> aliases) {
        String normalized = normalizeName(actualName);
        return aliases.stream().map(this::normalizeName).anyMatch(normalized::equals);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSpeedLike(String normalizedName) {
        return normalizedName.contains("speed")
                || normalizedName.contains("rpm")
                || normalizedName.contains("tach")
                || normalizedName.contains("rot")
                || normalizedName.contains("转速")
                || normalizedName.contains("转频");
    }

    private boolean isVibrationLike(String normalizedName) {
        return normalizedName.matches("an\\d+")
                || normalizedName.matches("ch\\d+")
                || normalizedName.contains("acc")
                || normalizedName.contains("accel")
                || normalizedName.contains("vib")
                || normalizedName.contains("bearing")
                || normalizedName.contains("振动")
                || normalizedName.contains("加速度");
    }

    private boolean isTorqueLike(String normalizedName) {
        return normalizedName.contains("torque") || normalizedName.contains("扭矩");
    }

    private boolean isTimeLike(String normalizedName) {
        return normalizedName.equals("time")
                || normalizedName.equals("t")
                || normalizedName.contains("timestamp");
    }

    private String fileStem(Path path) {
        String filename = path.getFileName() == null ? "mat-signal" : path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    private double[] readVector(MLArray array, String fieldName) {
        if (!(array instanceof MLNumericArray<?> numericArray)) {
            throw new BizException("Field " + fieldName + " is not numeric.");
        }

        int rows = numericArray.getM();
        int cols = numericArray.getN();
        if (rows > 1 && cols > 1) {
            throw new BizException("Field " + fieldName + " must be a single-channel vector.");
        }

        int size = rows * cols;
        double[] result = new double[size];
        int index = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Number value = numericArray.getReal(row, col);
                result[index++] = value.doubleValue();
            }
        }
        return result;
    }

    private double readScalar(MLArray array, String fieldName) {
        if (!(array instanceof MLNumericArray<?> numericArray)) {
            throw new BizException("Field " + fieldName + " is not scalar numeric.");
        }
        if (numericArray.getSize() != 1) {
            throw new BizException("Field " + fieldName + " must be scalar.");
        }
        Number value = numericArray.getReal(0, 0);
        return value.doubleValue();
    }

    private String readString(MLArray array, String fieldName) {
        if (!(array instanceof MLChar mlChar)) {
            throw new BizException("Field " + fieldName + " is not a string.");
        }
        String value = mlChar.getString(0);
        return value == null ? "" : value.trim();
    }

    private enum SignalPreference {
        VIBRATION,
        SPEED
    }

    private record SignalCandidate(String name, MLArray array, int length) {
    }
}
