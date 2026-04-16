package com.kama.jchatmind.service.impl;

import com.jmatio.io.MatFileWriter;
import com.jmatio.types.MLArray;
import com.jmatio.types.MLChar;
import com.jmatio.types.MLDouble;
import com.kama.jchatmind.service.vibration.VibrationModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatFileParserServiceImplTest {

    private final MatFileParserServiceImpl parser = new MatFileParserServiceImpl();

    @TempDir
    Path tempDir;

    @Test
    void parse_shouldReadLegacySignalFields() throws Exception {
        File file = writeMatFile("legacy.mat", List.of(
                new MLDouble("signal", new double[][]{{1.0, 2.0, 3.0, 4.0}}),
                new MLDouble("sampleRate", new double[][]{{2048.0}}),
                new MLChar("unit", "m/s2"),
                new MLChar("deviceName", "bearing-A")
        ));

        VibrationModels.SignalData signalData = parser.parse(file.toPath());

        assertEquals("signal", signalData.getSignalName());
        assertEquals(2048.0, signalData.getSampleRate());
        assertEquals("m/s2", signalData.getUnit());
        assertEquals("bearing-A", signalData.getDeviceName());
        assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0}, signalData.getSamples());
    }

    @Test
    void parseVibration_shouldSelectTopLevelAnChannelFromMultiSignalMat() throws Exception {
        File file = writeMatFile("multi-channel.mat", List.of(
                new MLDouble("Fs", new double[][]{{25600.0}}),
                new MLDouble("AN7", new double[][]{{1.0}, {2.0}, {3.0}, {4.0}}),
                new MLDouble("Speed", new double[][]{{1795.0}, {1796.0}, {1797.0}, {1798.0}}),
                new MLDouble("Torque", new double[][]{{20.0}, {21.0}, {20.5}, {21.5}})
        ));

        VibrationModels.SignalData signalData = parser.parseVibration(file.toPath());

        assertEquals("AN7", signalData.getSignalName());
        assertEquals(25600.0, signalData.getSampleRate());
        assertEquals("unknown", signalData.getUnit());
        assertTrue(signalData.getDeviceName().startsWith("multi-channel"));
        assertArrayEquals(new double[]{1.0, 2.0, 3.0, 4.0}, signalData.getSamples());
    }

    @Test
    void parseSpeed_shouldSelectSpeedChannelFromMultiSignalMat() throws Exception {
        File file = writeMatFile("speed-channel.mat", List.of(
                new MLDouble("sample_rate", new double[][]{{10.0}}),
                new MLDouble("AN10", new double[][]{{1.0}, {2.0}, {3.0}, {4.0}}),
                new MLDouble("Speed", new double[][]{{1795.0}, {1796.0}, {1797.0}, {1798.0}})
        ));

        VibrationModels.SignalData signalData = parser.parseSpeed(file.toPath());

        assertEquals("Speed", signalData.getSignalName());
        assertEquals(10.0, signalData.getSampleRate());
        assertEquals("rpm", signalData.getUnit());
        assertArrayEquals(new double[]{1795.0, 1796.0, 1797.0, 1798.0}, signalData.getSamples());
    }

    @Test
    void parse_shouldFallbackToFixed40kSampleRateWhenMissingHints() throws Exception {
        File file = writeMatFile("missing-rate.mat", List.of(
                new MLDouble("AN3", new double[][]{{1.0}, {2.0}, {3.0}})
        ));

        VibrationModels.SignalData signalData = parser.parse(file.toPath());

        assertEquals("AN3", signalData.getSignalName());
        assertEquals(40000.0, signalData.getSampleRate());
    }

    private File writeMatFile(String filename, List<MLArray> arrays) throws Exception {
        File file = tempDir.resolve(filename).toFile();
        new MatFileWriter(file, new ArrayList<>(arrays));
        return file;
    }
}
