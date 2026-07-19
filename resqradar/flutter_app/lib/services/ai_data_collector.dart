import 'dart:io';
import 'package:path_provider/path_provider.dart';

class AiDataCollector {
  static final AiDataCollector _instance = AiDataCollector._internal();
  factory AiDataCollector() => _instance;
  AiDataCollector._internal();

  File? _currentFile;
  bool _isRecording = false;

  Future<void> startNewSession() async {
    try {
      final dir = await getApplicationDocumentsDirectory();
      String dateStr = DateTime.now().toIso8601String().replaceAll(':', '-').split('.')[0];
      String filename = 'vitalfi_ai_training_$dateStr.csv';
      _currentFile = File('${dir.path}/$filename');
      
      // Escribir cabecera CSV
      await _currentFile!.writeAsString('Timestamp,DeviceMotion,DetectionLabel,Confidence,RSSI_Data\n');
      _isRecording = true;
      print('Grabación de datos IA iniciada: ${_currentFile!.path}');
    } catch (e) {
      print('Error iniciando recolección de datos IA: $e');
      _isRecording = false;
    }
  }

  Future<void> logDataRow({
    required List<double> rssiBuffer,
    required double deviceMotion,
    required String detectionLabel,
    required double confidence,
  }) async {
    if (!_isRecording || _currentFile == null) return;

    try {
      String timestamp = DateTime.now().toIso8601String();
      // Unir el buffer RSSI en una cadena separada por |
      String rssiStr = rssiBuffer.map((e) => e.toStringAsFixed(2)).join('|');
      
      String row = '$timestamp,${deviceMotion.toStringAsFixed(4)},$detectionLabel,${confidence.toStringAsFixed(2)},$rssiStr\n';
      await _currentFile!.writeAsString(row, mode: FileMode.append);
    } catch (e) {
      print('Error escribiendo datos IA: $e');
    }
  }

  void stopSession() {
    _isRecording = false;
    print('Grabación de datos IA detenida.');
  }
}
