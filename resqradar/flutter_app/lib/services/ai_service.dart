import 'package:flutter/services.dart';
import 'package:tflite_flutter/tflite_flutter.dart';

class AiInferenceService {
  Interpreter? _interpreter;

  Future<void> loadModel() async {
    try {
      // Cargar el modelo exportado desde Python (train.py -> model.tflite)
      _interpreter = await Interpreter.fromAsset('assets/model_vitalfi.tflite');
      print('Modelo de IA cargado correctamente.');
    } catch (e) {
      print('Error cargando el modelo: \$e');
    }
  }

  /// Pasa la señal de RSSI filtrada (array de floats) por la Red Neuronal
  /// para clasificar si es "Respiración Humana" o "Ruido/Escombros".
  Future<bool> detectLife(List<double> processedSignal) async {
    if (_interpreter == null) {
      print("⚠️ Advertencia: Modelo IA no cargado (Falta model_vitalfi.tflite en assets/). No se puede ejecutar la red neuronal.");
      return false;
    }

    try {
      // Formatear el input según el tensor de entrada esperado por el modelo exportado (Ej: [1, 200])
      var input = [processedSignal];
      // Formatear el output según el tensor de salida (Probabilidad de vida)
      var output = List.filled(1 * 1, 0.0).reshape([1, 1]);

      _interpreter!.run(input, output);
      
      // Si la probabilidad devuelta por la red neuronal es mayor a 75%, hay patrón de vida
      bool isLifeDetected = output[0][0] > 0.75;
      
      if (isLifeDetected) {
        // Feedback táctico real inyectado por la detección matemática de la IA
        HapticFeedback.heavyImpact(); 
      }
      return isLifeDetected;
    } catch (e) {
      print("Error ejecutando modelo IA: $e");
      return false;
    }
  }

  void dispose() {
    // _interpreter?.close();
  }
}
