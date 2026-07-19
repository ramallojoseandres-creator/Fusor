import 'dart:math' as math;

class FftUtils {
  static int nextPowerOfTwo(int n) {
    int v = 1;
    while (v < n) v = v << 1;
    return v;
  }

  static Map<String, List<double>> magnitudeSpectrum(List<double> signal, double fs) {
    int n = nextPowerOfTwo(signal.length);
    List<double> re = List.filled(n, 0.0);
    List<double> im = List.filled(n, 0.0);
    for (int i = 0; i < signal.length; i++) {
      re[i] = signal[i];
    }
    _fftInPlace(re, im);

    int half = n ~/ 2;
    List<double> freqs = List.filled(half, 0.0);
    List<double> mags = List.filled(half, 0.0);
    for (int k = 0; k < half; k++) {
      freqs[k] = k * fs / n;
      mags[k] = math.sqrt(re[k] * re[k] + im[k] * im[k]);
    }
    return {'freqs': freqs, 'mags': mags};
  }

  static void _fftInPlace(List<double> re, List<double> im) {
    int n = re.length;
    int j = 0;
    for (int i = 1; i < n; i++) {
      int bit = n >> 1;
      while ((j & bit) != 0) {
        j ^= bit;
        bit >>= 1;
      }
      j ^= bit;
      if (i < j) {
        double tempRe = re[i];
        re[i] = re[j];
        re[j] = tempRe;
        double tempIm = im[i];
        im[i] = im[j];
        im[j] = tempIm;
      }
    }

    int len = 2;
    while (len <= n) {
      double ang = -2.0 * math.pi / len;
      double wLenRe = math.cos(ang);
      double wLenIm = math.sin(ang);
      int i = 0;
      while (i < n) {
        double wRe = 1.0;
        double wIm = 0.0;
        for (int k = 0; k < len ~/ 2; k++) {
          double uRe = re[i + k];
          double uIm = im[i + k];
          double vRe = re[i + k + len ~/ 2] * wRe - im[i + k + len ~/ 2] * wIm;
          double vIm = re[i + k + len ~/ 2] * wIm + im[i + k + len ~/ 2] * wRe;
          re[i + k] = uRe + vRe;
          im[i + k] = uIm + vIm;
          re[i + k + len ~/ 2] = uRe - vRe;
          im[i + k + len ~/ 2] = uIm - vIm;
          double nextWRe = wRe * wLenRe - wIm * wLenIm;
          wIm = wRe * wLenIm + wIm * wLenRe;
          wRe = nextWRe;
        }
        i += len;
      }
      len <<= 1;
    }
  }

  static SpectralMetrics spectralMetrics(List<double> signal, double fs, List<double> band) {
    var spectrum = magnitudeSpectrum(signal, fs);
    List<double> freqs = spectrum['freqs']!;
    List<double> mags = spectrum['mags']!;
    
    double bestF = 0.0;
    double bestM = 0.0;
    double inBand = 0.0;
    double outBand = 0.0;
    double total = 0.0;
    
    for (int i = 0; i < freqs.length; i++) {
      double e = mags[i] * mags[i];
      total += e;
      if (freqs[i] >= band[0] && freqs[i] <= band[1]) {
        inBand += e;
        if (mags[i] > bestM) {
          bestM = mags[i];
          bestF = freqs[i];
        }
      } else {
        outBand += e;
      }
    }
    double specEnergy = (total < 1e-12) ? 0.0 : inBand / total;
    double snr = (outBand < 1e-12) ? 100.0 : inBand / outBand;
    return SpectralMetrics(bestF, bestM, specEnergy, snr);
  }
}

class SpectralMetrics {
  final double dominantFreq;
  final double dominantMag;
  final double spectralEnergy;
  final double snr;
  SpectralMetrics(this.dominantFreq, this.dominantMag, this.spectralEnergy, this.snr);
}

class SignalFilters {
  static List<double> hampelFilter(List<double> signal, {int windowSize = 5, double threshold = 3.5}) {
    List<double> out = List.from(signal);
    double k = 1.4826;
    for (int i = 0; i < signal.length; i++) {
      int lo = math.max(0, i - windowSize);
      int hi = math.min(signal.length, i + windowSize + 1);
      List<double> window = signal.sublist(lo, hi)..sort();
      double median = window[window.length ~/ 2];
      
      List<double> absDevs = window.map((e) => (e - median).abs()).toList()..sort();
      double mad = k * absDevs[absDevs.length ~/ 2];
      
      if (mad > 0 && (signal[i] - median).abs() > threshold * mad) {
        out[i] = median;
      }
    }
    return out;
  }

  static List<double> removeSlowTrend(List<double> signal, double fs, double windowSec) {
    int win = math.max(3, (fs * windowSec).toInt());
    if (signal.length < win) return List.from(signal);
    List<double> out = List.from(signal);
    int half = win ~/ 2;
    for (int i = 0; i < signal.length; i++) {
      int lo = math.max(0, i - half);
      int hi = math.min(signal.length, i + half + 1);
      double sum = 0;
      for (int j = lo; j < hi; j++) sum += signal[j];
      double mean = sum / (hi - lo);
      out[i] -= mean;
    }
    return out;
  }

  static List<double> bandpass(List<double> signal, double lowHz, double highHz, double fs) {
    List<double> detrended = removeSlowTrend(signal, fs, math.max(5.0, 1.0 / lowHz));
    return _lowPass(detrended, highHz, fs);
  }

  static List<double> _lowPass(List<double> signal, double cutoffHz, double fs) {
    double rc = 1.0 / (2.0 * math.pi * cutoffHz);
    double dt = 1.0 / fs;
    double alpha = dt / (rc + dt);
    List<double> out = List.filled(signal.length, 0.0);
    out[0] = signal[0];
    for (int i = 1; i < signal.length; i++) {
      out[i] = out[i - 1] + alpha * (signal[i] - out[i - 1]);
    }
    return out;
  }

  static List<double> preprocess(List<double> signal, double fs, bool rubbleMode) {
    double avg = signal.isEmpty ? 0 : signal.reduce((a, b) => a + b) / signal.length;
    List<double> x = signal.map((e) => e - avg).toList();
    
    List<double> d1 = List.filled(x.length, 0.0);
    for (int i = 0; i < x.length; i++) {
      d1[i] = (i == 0) ? x[0] : x[i] - x[i - 1];
    }
    double d1Mean = d1.reduce((a, b) => a + b) / d1.length;
    for (int i = 0; i < d1.length; i++) d1[i] -= d1Mean;

    if (!rubbleMode) {
      return List.generate(x.length, (i) => 0.65 * x[i] + 0.35 * d1[i]);
    }

    List<double> d2 = List.filled(d1.length, 0.0);
    for (int i = 0; i < d1.length; i++) {
      d2[i] = (i == 0) ? d1[0] : d1[i] - d1[i - 1];
    }
    double d2Mean = d2.reduce((a, b) => a + b) / d2.length;
    for (int i = 0; i < d2.length; i++) d2[i] -= d2Mean;

    int win = math.max(5, (fs * 15).toInt());
    List<double> trendRemoved = x;
    if (x.length >= win) {
      trendRemoved = List.filled(x.length, 0.0);
      for (int i = 0; i < x.length; i++) {
        int lo = math.max(0, i - win ~/ 2);
        int hi = math.min(x.length, i + win ~/ 2 + 1);
        double sum = 0;
        for (int j = lo; j < hi; j++) sum += x[j];
        double mean = sum / (hi - lo);
        trendRemoved[i] = x[i] - mean;
      }
    }
    
    return List.generate(trendRemoved.length, (i) => 0.45 * trendRemoved[i] + 0.35 * d1[i] + 0.20 * d2[i]);
  }

  static double respiratoryPeriodicity(List<double> filtered, double fs) {
    if (filtered.length < math.max(30, (fs * 12).toInt())) return 0.0;
    double mean = filtered.reduce((a, b) => a + b) / filtered.length;
    List<double> centered = filtered.map((e) => e - mean).toList();
    List<double> ac = _correlate(centered, centered);
    if (ac.isEmpty || ac[0] <= 1e-12) return 0.0;
    
    List<double> norm = ac.map((e) => e / ac[0]).toList();
    int lagMin = math.max(1, (fs / 0.55).toInt());
    int lagMax = math.min(norm.length - 1, (fs / 0.06).toInt());
    if (lagMax <= lagMin) return 0.0;
    
    double maxVal = -double.maxFinite;
    for (int i = lagMin; i < lagMax; i++) {
      if (norm[i] > maxVal) maxVal = norm[i];
    }
    return maxVal;
  }

  static List<double> _correlate(List<double> a, List<double> b) {
    int n = a.length;
    List<double> out = List.filled(2 * n - 1, 0.0);
    for (int i = 0; i < out.length; i++) {
      double sum = 0.0;
      for (int j = 0; j < a.length; j++) {
        int k = i - (n - 1) + j;
        if (k >= 0 && k < b.length) sum += a[j] * b[k];
      }
      out[i] = sum;
    }
    return out.sublist(n - 1);
  }

  static int uniqueRounded(List<double> signal, {int decimals = 2}) {
    double factor = math.pow(10.0, decimals.toDouble()).toDouble();
    Set<double> unique = {};
    for (double v in signal) {
      unique.add((v * factor).round() / factor);
    }
    return unique.length;
  }

  static double variance(List<double> signal) {
    if (signal.isEmpty) return 0.0;
    double mean = signal.reduce((a, b) => a + b) / signal.length;
    double sumSq = 0;
    for (double v in signal) sumSq += (v - mean) * (v - mean);
    return sumSq / signal.length;
  }
}
