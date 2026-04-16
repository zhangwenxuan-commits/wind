package com.kama.jchatmind.service.impl;

import com.kama.jchatmind.service.vibration.VibrationModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VibrationAnalysisServiceImplTest {

    @Test
    void shouldCalculateBearingCharacteristicFrequencies() {
        VibrationAnalysisServiceImpl service = new VibrationAnalysisServiceImpl(null, null, null, null);

        VibrationModels.BearingCharacteristicFrequencies result =
                service.calculateBearingCharacteristicFrequencies(10.0, 8, 10.0, 50.0, 0.0);

        assertEquals(10.0, result.getShaftFrequencyHz(), 1e-6);
        assertEquals(4.0, result.getCageFrequencyHz(), 1e-6);
        assertEquals(24.0, result.getBallSpinFrequencyHz(), 1e-6);
        assertEquals(32.0, result.getOuterRaceFrequencyHz(), 1e-6);
        assertEquals(48.0, result.getInnerRaceFrequencyHz(), 1e-6);
    }

    @Test
    void shouldBuildWindTurbineReferenceProfileForHighSpeedShaft() {
        VibrationAnalysisServiceImpl service = new VibrationAnalysisServiceImpl(null, null, null, null);

        VibrationModels.WindTurbineReferenceProfile profile =
                service.buildWindTurbineReferenceProfile("HSS", 1800.0);

        assertEquals("HSS", profile.getReferenceShaft());
        assertEquals(1800.0, profile.getReferenceRpm(), 1e-6);
        assertEquals(30.0, profile.getReferenceFrequencyHz(), 1e-6);
        assertNotNull(profile.getEntries());

        VibrationModels.ReferenceFrequencyEntry generatorShaft = profile.getEntries().stream()
                .filter(entry -> "generator shaft".equals(entry.getComponent()))
                .findFirst()
                .orElseThrow();
        assertEquals(30.0, generatorShaft.getExpectedFrequencyHz(), 1e-6);

        VibrationModels.ReferenceFrequencyEntry hssOuterRace = profile.getEntries().stream()
                .filter(entry -> "hss A1&A2".equals(entry.getComponent()) && "outer race".equals(entry.getFeature()))
                .findFirst()
                .orElseThrow();
        assertEquals(8.490 * 30.0, hssOuterRace.getExpectedFrequencyHz(), 1e-6);
    }

    @Test
    void shouldTreatPointOneOrderDifferenceAsApproximateMatch() {
        VibrationAnalysisServiceImpl service = new VibrationAnalysisServiceImpl(null, null, null, null);

        VibrationModels.ReferenceFrequencyEntry referenceEntry = VibrationModels.ReferenceFrequencyEntry.builder()
                .category("bearing")
                .component("demo-bearing")
                .feature("outer race")
                .expectedFrequencyHz(30.0)
                .build();

        VibrationModels.ReferenceFrequencyMatch match = service.matchReferenceEntry(
                referenceEntry,
                java.util.List.of(
                        VibrationModels.SpectrumPeak.builder()
                                .frequencyHz(33.0)
                                .amplitude(8.2)
                                .build()
                ),
                30.0,
                0.03
        );

        assertTrue(match.isMatched());
        assertEquals(33.0, match.getObservedFrequencyHz(), 1e-6);
        assertEquals(0.10, match.getOrderError(), 1e-6);
        assertEquals("order_tolerance", match.getMatchBasis());
    }

    @Test
    void shouldKeepNearestPeakVisibleWhenOutsideAllTolerances() {
        VibrationAnalysisServiceImpl service = new VibrationAnalysisServiceImpl(null, null, null, null);

        VibrationModels.ReferenceFrequencyEntry referenceEntry = VibrationModels.ReferenceFrequencyEntry.builder()
                .category("bearing")
                .component("demo-bearing")
                .feature("outer race")
                .expectedFrequencyHz(30.0)
                .build();

        VibrationModels.ReferenceFrequencyMatch match = service.matchReferenceEntry(
                referenceEntry,
                java.util.List.of(
                        VibrationModels.SpectrumPeak.builder()
                                .frequencyHz(36.0)
                                .amplitude(4.5)
                                .build()
                ),
                30.0,
                0.03
        );

        assertFalse(match.isMatched());
        assertEquals(36.0, match.getObservedFrequencyHz(), 1e-6);
        assertEquals(6.0, match.getFrequencyErrorHz(), 1e-6);
        assertEquals(0.20, match.getOrderError(), 1e-6);
        assertEquals("nearest_peak_only", match.getMatchBasis());
    }
}
