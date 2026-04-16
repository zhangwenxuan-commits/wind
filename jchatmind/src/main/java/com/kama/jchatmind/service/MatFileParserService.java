package com.kama.jchatmind.service;

import com.kama.jchatmind.service.vibration.VibrationModels;

import java.io.IOException;
import java.nio.file.Path;

public interface MatFileParserService {
    VibrationModels.SignalData parse(Path path) throws IOException;
    VibrationModels.SignalData parseVibration(Path path) throws IOException;
    VibrationModels.SignalData parseSpeed(Path path) throws IOException;
    VibrationModels.MatSignalCatalog inspect(Path path) throws IOException;
}
