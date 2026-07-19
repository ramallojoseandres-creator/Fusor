import 'dart:convert';
import 'package:flutter/services.dart';

class WifiScannerFlutter {
  static const MethodChannel _channel = MethodChannel('com.solvitco.vitalfi/wifi');
  static const EventChannel _eventChannel = EventChannel('com.solvitco.vitalfi/wifi_events');
  
  static Future<bool> startScanner() async {
    try {
      final bool result = await _channel.invokeMethod('startScanner');
      return result;
    } on PlatformException catch (e) {
      print("Failed to start scanner: '\${e.message}'.");
      return false;
    }
  }

  static Future<bool> stopScanner() async {
    try {
      final bool result = await _channel.invokeMethod('stopScanner');
      return result;
    } on PlatformException catch (e) {
      print("Failed to stop scanner: '\${e.message}'.");
      return false;
    }
  }

  static Stream<double> get rssiStream {
    return _eventChannel.receiveBroadcastStream().map((dynamic event) {
      final String jsonStr = event as String;
      final Map<String, dynamic> data = jsonDecode(jsonStr);
      return (data['rssi'] as num).toDouble();
    });
  }
}
