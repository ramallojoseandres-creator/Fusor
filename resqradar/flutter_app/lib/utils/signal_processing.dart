import 'dart:math';

class SignalProcessing {
  /// Filtro Hampel para remover valores atípicos (outliers) en la señal RSSI
  static List<double> applyHampelFilter(List<double> signal, int windowSize, double nSigmas) {
    if (signal.length < windowSize * 2 + 1) return signal;
    
    List<double> filtered = List.from(signal);
    for (int i = windowSize; i < signal.length - windowSize; i++) {
      List<double> window = signal.sublist(i - windowSize, i + windowSize + 1);
      window.sort();
      double median = window[windowSize]; // Elemento central
      
      List<double> deviations = window.map((val) => (val - median).abs()).toList();
      deviations.sort();
      double mad = deviations[windowSize];
      
      double threshold = nSigmas * 1.4826 * mad;
      if ((signal[i] - median).abs() > threshold) {
        filtered[i] = median;
      }
    }
    return filtered;
  }

  /// Filtro Pasabanda (Bandpass)
  /// En una implementación real se usarían coeficientes calculados para 0.1Hz - 0.7Hz
  /// en función del 'Sampling Rate' del dispositivo.
  static List<double> applyBandpassFilter(List<double> signal) {
    // Marcador de posición para convolución FIR/IIR
    return signal.map((v) => v * 0.8).toList(); 
  }
}
