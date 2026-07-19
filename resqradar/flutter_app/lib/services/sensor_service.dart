import 'dart:async';
import 'package:sensors_plus/sensors_plus.dart';
import 'package:flutter_compass/flutter_compass.dart';

class SensorService {
  StreamSubscription? _accelSub;
  StreamSubscription? _magSub;
  
  double xAccel = 0, yAccel = 0, zAccel = 0;
  double heading = 0;
  double motionNoise = 0.0; // Nivel de agitación del teléfono

  void startListening() {
    _accelSub = accelerometerEventStream().listen((AccelerometerEvent event) {
      xAccel = event.x;
      yAccel = event.y;
      zAccel = event.z;
      
      // Calibración dinámica: Calculamos la fuerza G adicional (gravedad base es ~96 en magnitud)
      double magnitude = (event.x * event.x) + (event.y * event.y) + (event.z * event.z);
      motionNoise = (magnitude - 96.0).abs();
    });

    _magSub = FlutterCompass.events?.listen((CompassEvent event) {
      // Usamos el heading (Azimut) directo desde el Sensor Fusion
      // que incluye compensación de inclinación nativa
      if (event.heading != null) {
        heading = event.heading!;
      }
    });
  }

  void stopListening() {
    _accelSub?.cancel();
    _magSub?.cancel();
  }

  /// Cancelación de movimiento:
  /// Retorna true si el dispositivo se está moviendo demasiado (por encima de un umbral)
  /// lo que podría generar falsos positivos en el radar de respiración.
  bool isMovingTooMuch() {
    // Si la magnitud de aceleración > umbral, nos estamos moviendo
    double magnitude = (xAccel * xAccel) + (yAccel * yAccel) + (zAccel * zAccel);
    // ~120 equivale aproximadamente a picos por encima de la gravedad terrestre estándar
    return magnitude > 120.0; 
  }
}
