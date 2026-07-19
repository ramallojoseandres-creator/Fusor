import 'dart:math' as math;
import 'vital_signal_detector.dart';

class HeatPoint {
  final double x;
  final double y;
  final double intensity;
  HeatPoint(this.x, this.y, this.intensity);
}

class TrappedVictim {
  final int id;
  final String label;
  final double x;
  final double y;
  final double bearing;
  final double distance;
  final double depth;
  final bool isBreathing;
  final bool isActivity;
  final bool heartBeating;
  final double heartRateBpm;
  final double respRate;
  final double confidence;
  final double rubbleConfidence;
  final String proximity;
  final String status;
  final double score;
  final int lastSeenMs;

  TrappedVictim({
    required this.id,
    required this.label,
    required this.x,
    required this.y,
    required this.bearing,
    required this.distance,
    required this.depth,
    required this.isBreathing,
    required this.isActivity,
    required this.heartBeating,
    required this.heartRateBpm,
    required this.respRate,
    required this.confidence,
    required this.rubbleConfidence,
    required this.proximity,
    required this.status,
    required this.score,
    required this.lastSeenMs,
  });
}

class MultiVictimTracker {
  final int numBins;
  final double decay;

  late List<double> scores;
  late List<double> distances;
  late List<double> breathing;
  late List<double> activity;
  late List<double> confidence;
  late List<double> respRate;
  late List<double> heartRate;
  late List<double> rubble;
  late List<int> lastSeen;
  
  Map<int, int> binIds = {};
  int _nextVictimId = 1;

  MultiVictimTracker({this.numBins = 36, this.decay = 0.985}) {
    _initArrays();
  }

  void _initArrays() {
    scores = List.filled(numBins, 0.0);
    distances = List.filled(numBins, 0.0);
    breathing = List.filled(numBins, 0.0);
    activity = List.filled(numBins, 0.0);
    confidence = List.filled(numBins, 0.0);
    respRate = List.filled(numBins, 0.0);
    heartRate = List.filled(numBins, 0.0);
    rubble = List.filled(numBins, 0.0);
    lastSeen = List.filled(numBins, 0);
  }

  void reset() {
    _initArrays();
    binIds.clear();
    _nextVictimId = 1;
  }

  int _binIndex(double bearingDeg) {
    return ((bearingDeg / 360.0 * numBins).toInt()) % numBins;
  }

  void update({
    required double bearingDeg,
    required double distanceM,
    required double strength,
    required DetectionResult detection,
    required double rubbleConf,
    required String proximity,
    required int nowMs,
  }) {
    int idx = _binIndex(bearingDeg);
    for (int i = 0; i < numBins; i++) {
      scores[i] *= decay;
      distances[i] *= decay;
      breathing[i] *= decay;
      activity[i] *= decay;
      confidence[i] *= decay;
      respRate[i] *= decay;
      heartRate[i] *= decay;
      rubble[i] *= decay;
    }

    scores[idx] += strength;
    distances[idx] = (distances[idx] > 0) ? (distances[idx] * 0.75 + distanceM * 0.25) : distanceM;

    if (detection.isBreathing) breathing[idx] = math.min(1.0, breathing[idx] + 0.35);
    if (detection.isActivity) activity[idx] = math.min(1.0, activity[idx] + 0.25);
    confidence[idx] = math.min(1.0, confidence[idx] + detection.confidence * 0.3);
    
    if (detection.respRate > 0) {
      respRate[idx] = (respRate[idx] > 0) ? (respRate[idx] * 0.7 + detection.respRate * 0.3) : detection.respRate;
    }
    if (detection.heartRateBpm > 0) {
      heartRate[idx] = (heartRate[idx] > 0) ? (heartRate[idx] * 0.7 + detection.heartRateBpm * 0.3) : detection.heartRateBpm;
    }
    rubble[idx] = math.min(1.0, rubble[idx] + rubbleConf * 0.25);
    lastSeen[idx] = nowMs;
  }

  List<HeatPoint> heatmapPoints() {
    List<HeatPoint> points = [];
    double peak = scores.reduce(math.max);
    for (int i = 0; i < scores.length; i++) {
      if (scores[i] < math.max(0.005, peak * 0.1)) continue;
      double bearing = (i + 0.5) * 360.0 / numBins;
      double dist = (distances[i] > 0) ? distances[i] : 2.0;
      var xy = _polarToXY(dist, bearing);
      points.add(HeatPoint(xy[0], xy[1], scores[i]));
    }
    return points;
  }

  double depthUnderRubble(double distanceM, double confidence) {
    double depth = math.min(1.8, math.max(0.3, distanceM * 0.25));
    return -depth * (0.5 + 0.5 * math.min(1.0, confidence));
  }

  List<TrappedVictim> locateVictims(int nowMs, {double minScore = 0.025}) {
    List<int> peakBins = _findPeakBins(minScore);
    Set<int> usedIds = {};
    List<TrappedVictim> victims = [];
    for (int bin in peakBins) {
       victims.add(_buildVictim(bin, nowMs, usedIds));
    }
    victims.sort((a, b) => b.score.compareTo(a.score));
    return victims;
  }

  TrappedVictim _buildVictim(int bin, int nowMs, Set<int> usedIds) {
    double bearing = (bin + 0.5) * 360.0 / numBins;
    double dist = (distances[bin] > 0) ? distances[bin] : 2.0;
    var xy = _polarToXY(dist, bearing);
    double rubbleConf = math.max(0.0, math.min(1.0, rubble[bin]));
    double depth = depthUnderRubble(dist, math.max(0.2, rubbleConf));
    
    bool isBreathing = breathing[bin] > 0.18;
    bool isActivity = activity[bin] > 0.15;
    double hr = heartRate[bin];
    double rr = respRate[bin];
    double conf = math.max(0.0, math.min(1.0, confidence[bin]));
    bool heartBeating = hr >= 45.0 && (isBreathing || conf > 0.2);
    
    String proximity = _proximityLabel(dist, isBreathing, isActivity);
    String status = _victimStatus(isBreathing, isActivity, heartBeating, conf);
    int id = _victimIdForBin(bin, usedIds);

    return TrappedVictim(
      id: id,
      label: "P$id",
      x: xy[0],
      y: xy[1],
      bearing: bearing,
      distance: dist,
      depth: depth,
      isBreathing: isBreathing,
      isActivity: isActivity,
      heartBeating: heartBeating,
      heartRateBpm: hr,
      respRate: rr,
      confidence: conf,
      rubbleConfidence: rubbleConf,
      proximity: proximity,
      status: status,
      score: scores[bin],
      lastSeenMs: (lastSeen[bin] > 0) ? lastSeen[bin] : nowMs,
    );
  }

  String _victimStatus(bool isBreathing, bool isActivity, bool heartBeating, double confidence) {
    if (isBreathing && heartBeating) return "Viva — respira y pulso detectado";
    if (isBreathing) return "Viva — respiración detectada";
    if (heartBeating) return "Posible pulso cardíaco";
    if (isActivity) return "Actividad / movimiento";
    if (confidence > 0.2) return "Señal vital débil";
    return "Señal en análisis";
  }

  String _proximityLabel(double distance, bool isBreathing, bool isActivity) {
    if (distance < 1.2) return "MUY CERCA";
    if (distance < 2.2) return "CERCA";
    if (distance < 3.5) return "MEDIA";
    if (isActivity || isBreathing) return "LEJOS (señal)";
    return "LEJOS";
  }

  List<int> _findPeakBins(double minScore) {
    double peak = scores.reduce(math.max);
    if (peak <= 0) return [];
    // Absolute threshold based only on minScore so weak signals aren't suppressed by strong ones
    double threshold = minScore;
    List<int> peaks = [];
    for (int i = 0; i < scores.length; i++) {
      if (scores[i] < threshold) continue;
      double left = scores[(i - 1 + numBins) % numBins];
      double right = scores[(i + 1) % numBins];
      if (scores[i] >= left && scores[i] >= right) peaks.add(i);
    }
    return _mergeAdjacentPeaks(peaks);
  }

  List<int> _mergeAdjacentPeaks(List<int> peaks) {
    if (peaks.isEmpty) return [];
    peaks.sort();
    List<int> merged = [];
    List<int> group = [peaks.first];
    for (int i = 1; i < peaks.length; i++) {
      int prev = peaks[i - 1];
      int cur = peaks[i];
      bool adjacent = (cur - prev <= 2) || (prev <= 1 && cur >= numBins - 2);
      if (adjacent) {
        group.add(cur);
      } else {
        merged.add(group.reduce((a, b) => scores[a] > scores[b] ? a : b));
        group = [cur];
      }
    }
    merged.add(group.reduce((a, b) => scores[a] > scores[b] ? a : b));
    return merged;
  }

  int _victimIdForBin(int bin, Set<int> usedIds) {
    if (binIds.containsKey(bin) && !usedIds.contains(binIds[bin])) {
      usedIds.add(binIds[bin]!);
      return binIds[bin]!;
    }
    // Search nearby bins up to 90 degrees away to tolerate phone rotation
    for (int d = 1; d <= 9; d++) {
      for (int sign in [1, -1]) {
        int adj = (bin + d * sign + numBins) % numBins;
        if (binIds.containsKey(adj)) {
          int potentialId = binIds[adj]!;
          if (!usedIds.contains(potentialId)) {
            binIds[bin] = potentialId;
            usedIds.add(potentialId);
            return potentialId;
          }
        }
      }
    }
    int id = _nextVictimId++;
    binIds[bin] = id;
    usedIds.add(id);
    return id;
  }

  List<double> _polarToXY(double dist, double bearingDeg) {
    // Convierte rumbo geográfico a radianes matemáticos
    double rad = (90 - bearingDeg) * math.pi / 180.0;
    return [dist * math.cos(rad), dist * math.sin(rad)];
  }
}
