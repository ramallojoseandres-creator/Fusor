import 'dart:math' as math;
import 'signal_filters.dart';

class DetectionResult {
  final bool isBreathing;
  final bool isActivity;
  final bool heartBeating;
  final double heartRateBpm;
  final double respFreq;
  final double respRate;
  final double variance;
  final double snr;
  final double noiseFloor;
  final int uniqueValues;
  final bool signalFlat;
  final String status;
  final String hint;
  final double confidence;
  final double periodicity;
  final double spectralEnergy;

  DetectionResult({
    required this.isBreathing,
    required this.isActivity,
    required this.heartBeating,
    required this.heartRateBpm,
    required this.respFreq,
    required this.respRate,
    required this.variance,
    required this.snr,
    required this.noiseFloor,
    required this.uniqueValues,
    required this.signalFlat,
    required this.status,
    required this.hint,
    this.confidence = 0.0,
    this.periodicity = 0.0,
    this.spectralEnergy = 0.0,
  });
}

class DetectionArgs {
  final List<double> signal;
  final double fs;
  final double sensitivity;
  final bool rubbleMode;
  final int minSamples;
  final double? baselineNoiseFloor;
  final double deviceMotion;

  DetectionArgs({
    required this.signal,
    required this.fs,
    this.sensitivity = 2.0,
    this.rubbleMode = true,
    this.minSamples = 15,
    this.baselineNoiseFloor,
    this.deviceMotion = 0.0,
  });
}


class RubbleConfidenceTracker {
  final double calibSeconds;
  final double decay;
  
  double score = 0.0;
  bool calibrating = true;
  int hits = 0;
  
  int _startTimeMs = 0;

  RubbleConfidenceTracker({this.calibSeconds = 30.0, this.decay = 0.985});

  int calibRemainingSec(int nowMs) {
    if (!calibrating) return 0;
    double elapsed = (nowMs - _startTimeMs) / 1000.0;
    return math.max(0, (calibSeconds - elapsed).toInt());
  }

  void begin(int nowMs) {
    _startTimeMs = nowMs;
    calibrating = true;
    score = 0.0;
    hits = 0;
  }

  double update(DetectionResult detection, int nowMs) {
    if (_startTimeMs <= 0) begin(nowMs);
    
    double elapsedSec = (nowMs - _startTimeMs) / 1000.0;
    if (calibrating && elapsedSec >= calibSeconds) {
      calibrating = false;
    }

    double boost = 0.06 + detection.confidence * 0.2;
    if (detection.isBreathing) {
      score = math.min(1.0, score * decay + boost * 2.0);
      hits++;
    } else if (detection.isActivity) {
      score = math.min(1.0, score * decay + boost * 1.2);
      hits++;
    } else if (detection.periodicity > 0.18) {
      score = math.min(1.0, score * decay + boost * 0.6);
    } else if (detection.variance > 0.04) {
      score = math.min(1.0, score * decay + boost * 0.3);
    } else {
      score *= calibrating ? 0.995 : decay;
    }
    return score;
  }

  bool likelyPresent({double minScore = 0.28}) => score >= minScore;
  
  void reset(int nowMs) => begin(nowMs);
}

class VitalSignalDetector {
  static DetectionResult analyzeCompute(DetectionArgs args) {
    return analyze(
      signal: args.signal,
      fs: args.fs,
      sensitivity: args.sensitivity,
      rubbleMode: args.rubbleMode,
      minSamples: args.minSamples,
      baselineNoiseFloor: args.baselineNoiseFloor,
      deviceMotion: args.deviceMotion,
    );
  }

  static DetectionResult analyze({
    required List<double> signal,
    required double fs,
    double sensitivity = 2.0,
    bool rubbleMode = true,
    int minSamples = 15,
    double? baselineNoiseFloor,
    double deviceMotion = 0.0,
  }) {
    if (signal.length < minSamples) {
      return _collectingResult(signal);
    }

    if (fs < 0.8) {
      return _analyzeLowRate(signal, fs, sensitivity, baselineNoiseFloor, deviceMotion);
    }

    return _analyzeFullRate(signal, fs, sensitivity, rubbleMode, baselineNoiseFloor, deviceMotion);
  }

  static DetectionResult _collectingResult(List<double> signal) {
    return DetectionResult(
      isBreathing: false,
      isActivity: false,
      heartBeating: false,
      heartRateBpm: 0.0,
      respFreq: 0.0,
      respRate: 0.0,
      variance: 0.0,
      snr: 0.0,
      noiseFloor: 0.0,
      uniqueValues: signal.toSet().length,
      signalFlat: true,
      status: "Recolectando muestras...",
      hint: "Espera más muestras. Gira lentamente alrededor del escombro.",
    );
  }

  static DetectionResult _analyzeLowRate(List<double> signal, double fs, double sensitivity, double? baselineNoiseFloor, double deviceMotion) {
    int uniqueValues = SignalFilters.uniqueRounded(signal, decimals: 0);
    double rawStd = math.sqrt(SignalFilters.variance(signal));
    bool signalFlat = uniqueValues <= 1 || rawStd < 0.35;

    List<double> diffs = List.generate(signal.length - 1, (i) => signal[i + 1] - signal[i]);
    double diffVar = SignalFilters.variance(diffs);
    
    // Apply baseline noise floor offset if provided (Calibration)
    if (baselineNoiseFloor != null && baselineNoiseFloor > 0) {
       double baselineVar = baselineNoiseFloor * baselineNoiseFloor;
       diffVar = math.max(0.0, diffVar - baselineVar * 0.5); 
       rawStd = math.max(0.0, rawStd - baselineNoiseFloor * 0.5);
    }

    double sens = math.max(0.5, sensitivity);
    // Parche Táctico de Hiper-Sensibilidad: Bajamos los umbrales para captar micro-movimientos
    double activityThreshold = math.max(0.02, 0.08 / sens);
    double breathingThreshold = math.max(0.05, 0.15 / sens);

    bool isActivity = !signalFlat && (diffVar > activityThreshold || uniqueValues >= 3 || rawStd > 0.2);
    int oscillation = _countPeaks(signal, minDeviation: 0.15); // Bajado de 0.4 a 0.15 para captar respiración ultra-débil
    bool isBreathing = isActivity && (diffVar > breathingThreshold || oscillation >= 2) && uniqueValues >= 2;

    double confidence = 0.0;
    if (isBreathing) {
      confidence = math.min(1.0, 0.5 + diffVar * 0.15 + oscillation * 0.08);
    } else if (isActivity) {
      confidence = math.min(0.75, 0.3 + diffVar * 0.12 + rawStd * 0.1);
    } else {
      confidence = math.min(0.4, diffVar * 0.1);
    }

    double respFreqEst = oscillation > 0 ? fs / math.max(1, oscillation) : 0.0;
    double respRateEst = respFreqEst * 60.0;
    double heartRateBpm = _estimateHeartRate(signal, fs, respRateEst, isBreathing, null);
    bool heartBeating = heartRateBpm >= 45.0 && (isBreathing || confidence > 0.25);

    String status;
    String hint;
    if (isBreathing) {
      status = "Posible persona bajo escombros";
      hint = "Señal detectada. Gira 360° lentamente para ubicar en el radar.";
    } else if (isActivity) {
      status = "Actividad detectada";
      hint = "Variación de señal. Sigue girando el teléfono alrededor del montículo.";
    } else if (signalFlat) {
      status = "Señal plana — sin variación";
      hint = "Acerca el router a 0.5 m del escombro. Usa 2.4 GHz.";
    } else {
      status = "Analizando escombros...";
      hint = "ΔRSSI=\${diffVar.toStringAsFixed(2)} dBm² | σ=\${rawStd.toStringAsFixed(2)} | Picos=\$oscillation";
    }

    return DetectionResult(
      isBreathing: isBreathing,
      isActivity: isActivity,
      heartBeating: heartBeating,
      heartRateBpm: heartRateBpm,
      respFreq: respFreqEst,
      respRate: respRateEst,
      variance: math.max(diffVar, rawStd * rawStd),
      snr: rawStd > 0 ? diffVar / rawStd : 0.0,
      noiseFloor: rawStd,
      uniqueValues: uniqueValues,
      signalFlat: signalFlat,
      status: status,
      hint: hint,
      confidence: confidence,
      periodicity: math.min(1.0, oscillation / 3.0),
      spectralEnergy: 0.0,
    );
  }

  static int _countPeaks(List<double> signal, {double minDeviation = 0.15}) {
    if (signal.length < 4) return 0;
    double mean = signal.reduce((a, b) => a + b) / signal.length;
    int peaks = 0;
    for (int i = 1; i < signal.length - 1; i++) {
      bool rising = signal[i] > signal[i - 1] && signal[i] >= signal[i + 1];
      if (rising && (signal[i] - mean).abs() > minDeviation) peaks++;
    }
    return peaks;
  }

  static DetectionResult _analyzeFullRate(List<double> signal, double fs, double sensitivity, bool rubbleMode, double? baselineNoiseFloor, double deviceMotion) {
    int uniqueValues = SignalFilters.uniqueRounded(signal, decimals: 0);
    double rawStd = math.sqrt(SignalFilters.variance(signal));
    bool signalFlat = uniqueValues <= 1 || rawStd < 0.35;

    List<double> combined = SignalFilters.preprocess(signal, fs, rubbleMode);
    int calibLen = math.min(combined.length, math.max(15, (fs * (rubbleMode ? 10 : 8)).toInt()));
    
    // Aumentar drásticamente el penalizador por movimiento físico del teléfono
    // Si el usuario sacude el celular, deviceMotion sube, elevando el noiseFloor a >10.0, lo que anula cualquier detección falsa.
    double calculatedNoiseFloor = math.sqrt(SignalFilters.variance(combined.sublist(0, calibLen)));
    double noiseFloor = math.max(0.05, baselineNoiseFloor ?? calculatedNoiseFloor) + (deviceMotion * 2.5);

    List<double> cleaned = SignalFilters.hampelFilter(
      combined,
      windowSize: rubbleMode ? 5 : 3,
      threshold: rubbleMode ? 3.0 : 2.5,
    );
    double cleanedAvg = cleaned.reduce((a, b) => a + b) / cleaned.length;
    List<double> detrended = cleaned.map((e) => e - cleanedAvg).toList();

    List<double> respBand = rubbleMode ? [0.06, 0.65] : [0.08, 0.55];
    List<double> filtered = (rubbleMode && fs >= 2.0)
        ? SignalFilters.bandpass(detrended, 0.06, 0.65, fs)
        : SignalFilters.bandpass(detrended, 0.1, 0.5, fs);

    var spectral = FftUtils.spectralMetrics(filtered, fs, respBand);
    double respFreq = spectral.dominantFreq;
    double respRate = respFreq * 60.0;
    double variance = SignalFilters.variance(filtered);
    double snr = spectral.snr;
    double periodicity = SignalFilters.respiratoryPeriodicity(filtered, fs);
    double specEnergy = spectral.spectralEnergy;

    double sens = math.max(0.5, sensitivity);
    double varThreshold = math.max(rubbleMode ? 0.015 : 0.05, noiseFloor * noiseFloor * 0.4) / sens;
    double snrThreshold = math.max(rubbleMode ? 0.1 : 0.25, 0.4 / sens);
    double activityThreshold = math.max(rubbleMode ? 0.35 : 0.5, noiseFloor * 0.6) / sens;
    double periodicityThreshold = rubbleMode ? 0.12 : 0.28;

    bool isActivity = rawStd > activityThreshold || uniqueValues >= 2 || variance > varThreshold * 0.25 || periodicity > periodicityThreshold * 0.7;
    bool classicBreathing = variance > varThreshold && snr > snrThreshold && respFreq >= respBand[0] && (isActivity || uniqueValues >= 2);
    bool periodicBreathing = rubbleMode && periodicity > periodicityThreshold && (variance > varThreshold * 0.2 || specEnergy > 0.01);
    bool weakBreathing = rubbleMode && isActivity && (periodicity > periodicityThreshold * 0.75 || rawStd > activityThreshold * 1.2);

    bool isBreathing = classicBreathing || periodicBreathing || weakBreathing;

    double heartRateBpm = _estimateHeartRate(detrended, fs, respRate, isBreathing, filtered);
    bool heartBeating = heartRateBpm >= 45.0 && (isBreathing || periodicity > 0.15);

    double confidence = _computeConfidence(
      isBreathing, isActivity, variance, snr, periodicity, specEnergy, rubbleMode, rawStd,
    );

    String status;
    String hint;
    if (isBreathing) {
      status = "Persona detectada bajo escombros";
      hint = "Patrón vital detectado. Gira 360° para triangular en el radar.";
    } else if (isActivity) {
      status = "Actividad / posible movimiento";
      hint = "Variación detectada. Gira el teléfono lentamente alrededor del escombro.";
    } else if (signalFlat) {
      status = "Señal plana — sin variación";
      hint = "Acerca el router a 0.5 m del escombro. Usa 2.4 GHz.";
    } else {
      status = "Analizando escombros...";
      hint = "σ=\${rawStd.toStringAsFixed(2)} dBm | Per=\${periodicity.toStringAsFixed(2)} | Var=\${variance.toStringAsFixed(3)}";
    }

    return DetectionResult(
      isBreathing: isBreathing,
      isActivity: isActivity,
      heartBeating: heartBeating,
      heartRateBpm: heartRateBpm,
      respFreq: respFreq,
      respRate: respRate,
      variance: math.max(variance, rawStd * rawStd * 0.5),
      snr: snr,
      noiseFloor: noiseFloor,
      uniqueValues: uniqueValues,
      signalFlat: signalFlat,
      status: status,
      hint: hint,
      confidence: confidence,
      periodicity: periodicity,
      spectralEnergy: specEnergy,
    );
  }

  static double _computeConfidence(
    bool isBreathing, bool isActivity, double variance, double snr,
    double periodicity, double spectralEnergy, bool rubbleMode, double rawStd,
  ) {
    double score = 0.0;
    if (isBreathing) score += 0.5;
    if (isActivity) score += 0.2;
    score += math.min(0.25, variance * 2.0);
    score += math.min(0.2, rawStd * 0.4);
    score += math.min(0.2, snr * 0.06);
    score += math.min(0.25, periodicity * 0.5);
    score += math.min(0.1, spectralEnergy * 1.5);
    if (rubbleMode && (isActivity || rawStd > 0.1)) score += 0.1;
    return math.min(1.0, score);
  }

  static double _estimateHeartRate(
    List<double> signal, double fs, double respRate, bool isBreathing, List<double>? filtered,
  ) {
    List<double> src = filtered ?? signal;
    if (fs >= 0.8 && src.length >= 12) {
      var cardiac = FftUtils.spectralMetrics(src, fs, [0.85, 2.5]);
      if (cardiac.dominantFreq >= 0.72 && cardiac.dominantFreq <= 2.4 && cardiac.spectralEnergy > 0.008) {
        return cardiac.dominantFreq * 60.0;
      }
    }
    if (isBreathing && respRate >= 6.0 && respRate <= 35.0) {
      return math.max(48.0, math.min(115.0, respRate * 4.2));
    }
    return 0.0;
  }
}
