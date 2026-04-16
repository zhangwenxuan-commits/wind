# Wind Turbine Bearing Analysis Requirements

## Scope

This iteration focuses on wind-turbine drivetrain diagnosis with a workflow-driven agent.

The current workflow supports:

1. selecting the primary vibration MAT document
2. loading bearing parameters and diagnostic rules from the knowledge base
3. running base spectrum analysis
4. optionally running advanced analysis with envelope spectrum, speed analysis, order spectrum, and built-in reference-frequency matching
5. generating a structured diagnosis report

## Parameter Card Assumptions

The uploaded parameter-card images were normalized into a built-in reference-frequency catalog.

The implementation currently uses:

- shaft-frequency ratios relative to the high-speed shaft
- gear-mesh and single-tooth reference ratios
- bearing characteristic ratios for main bearing, HSS, ISS, LSS, hollow shaft, and carrier bearings

The built-in profile can be expanded at runtime with:

- reference shaft: `MS` or `HSS`
- reference speed: `referenceRpm`

The resulting profile is used by `matchWindTurbineReferenceProfile(...)`.

## Selected Parameters

For the first version, the system only needs the following parameter categories from the knowledge base:

1. bearing geometry
   - rolling element count
   - rolling element diameter
   - pitch diameter
   - contact angle
2. shaft information
   - shaft speed or shaft frequency
   - preferred reference shaft for the speed signal: `MS` or `HSS`
3. advanced-analysis hints
   - recommended envelope band, for example `2000-8000`
4. diagnosis rules
   - acceptable match tolerance for BPFO/BPFI or built-in reference frequencies
   - heuristic thresholds for crest factor, kurtosis, and high-frequency energy ratio

## MAT Data Contract

The MAT parser currently expects these signal fields:

- `signal`
- `sampleRate`
- `unit`
- `deviceName`

The parser now also supports a multi-signal MAT layout where the file contains several top-level vector variables, for example:

- `AN7`, `AN10` for vibration-like channels
- `Speed` for rotational speed
- `Torque` for torque
- `Fs` or `sampleRate` for sampling frequency

In that layout:

- vibration analysis will automatically prefer vibration-like channels such as `AN*`, `CH*`, `acc*`, or `vib*`
- speed analysis will automatically prefer channels such as `Speed`, `RPM`, or `Tach`
- if both vibration and speed channels exist in the same MAT file, the workflow can reuse the same document for both roles
- if no sampling-rate field is present, the parser currently falls back to the agreed fixed rate `40000 Hz`

### Vibration MAT Recommendations

- use signal names that clearly indicate vibration or acceleration
- preferred units: `g`, `m/s^2`, `m/s2`, `mm/s`
- include bearing or channel hints in `signalName` or `deviceName`

### Speed MAT Recommendations

- use signal names with `speed`, `rpm`, `tach`, `rot`, or shaft labels
- preferred units: `rpm`, `Hz`, or `rps`
- include shaft hints in `signalName`, `deviceName`, or filename:
  - `HSS`, `high speed`, `generator`
  - `MS`, `main shaft`, `rotor`, `low speed`

The current implementation infers whether a MAT document is a vibration signal or a speed signal from:

- unit
- signal name
- device name
- filename

## Implemented Analysis Functions

The service layer now supports:

- `analyzeVibrationSpectrum(documentId)`
- `analyzeEnvelopeSpectrum(documentId, bandHint)`
- `analyzeSpeedSignal(documentId)`
- `analyzeOrderSpectrum(vibrationDocumentId, speedDocumentId, referenceShaft)`
- `buildWindTurbineReferenceProfile(referenceShaft, referenceRpm)`
- `matchWindTurbineReferenceProfile(vibrationDocumentId, speedDocumentId, referenceShaft, useEnvelope, toleranceRatio, bandHint)`
- `calculateBearingCharacteristicFrequencies(shaftFrequencyHz, rollingElementCount, rollingElementDiameterMm, pitchDiameterMm, contactAngleDeg)`

## Current Workflow Behavior

### Step 1: Select MAT Signals

- auto-select the vibration document if there is only one clear vibration candidate
- auto-select the speed document if there is only one clear speed candidate
- ask the user only when multiple vibration or speed candidates remain ambiguous

### Step 2: Load Parameter Context

- retrieve bearing geometry, shaft frequency or speed, envelope-band hints, and diagnosis rules from the knowledge base

### Step 3: Base Analysis

- run FFT-based spectrum analysis
- extract dominant peaks, crest factor, kurtosis, and high-frequency energy ratio

### Step 4: Advanced Analysis

- if impact features are present, run envelope analysis
- if a speed document is available, analyze the speed signal
- when vibration and speed documents are both available, run order-spectrum analysis or built-in reference-frequency matching
- if bearing geometry and shaft frequency are known, calculate FTF, BSF, BPFO, and BPFI

### Step 5: Generate Diagnosis

- summarize diagnosis conclusion
- list key evidence
- report risk level
- provide recommended action
- include uncertainty if evidence is incomplete

## Known Limitations

1. order-spectrum analysis currently normalizes by average speed; it is not yet a full angle-domain resampling pipeline
2. reference matching currently compares against dominant observed peaks, not the full spectrum
3. the built-in parameter-card profile is a first-pass implementation and should later be replaced or supplemented by structured parameter-card ingestion from the knowledge base

## Next Inputs Needed

To continue implementation with real data, the system next needs:

1. at least one vibration MAT document
2. at least one speed or tachometer MAT document
3. a Markdown parameter card in the knowledge base with:
   - bearing geometry
   - shaft speed or shaft frequency
   - reference shaft label
   - envelope-band recommendation
   - diagnosis thresholds or match tolerances
