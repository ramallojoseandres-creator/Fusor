import 'dart:io';
import 'package:flutter/foundation.dart';
import '../config/app_config.dart';

class CaptivePortalServer {
  HttpServer? _server;
  bool get isRunning => _server != null;
  final Function(String ip)? onVictimConnected;

  CaptivePortalServer({this.onVictimConnected});

  Future<void> start() async {
    if (isRunning) return;

    try {
      // Bind to all interfaces (0.0.0.0) on port from config
      _server = await HttpServer.bind(InternetAddress.anyIPv4, AppConfig.localPortalPort);
      debugPrint("Captive Portal Server running on port ${_server!.port}");

      _server!.listen((HttpRequest request) {
        _handleRequest(request);
      });
    } catch (e) {
      debugPrint("Error starting Captive Portal Server: $e");
      // Fallback to another port if default is taken
      try {
        _server = await HttpServer.bind(InternetAddress.anyIPv4, AppConfig.localPortalFallbackPort);
        debugPrint("Captive Portal Server running on fallback port ${AppConfig.localPortalFallbackPort}");
        _server!.listen((HttpRequest request) {
          _handleRequest(request);
        });
      } catch (e2) {
        debugPrint("Failed to start Captive Portal Server entirely.");
      }
    }
  }

  void _handleRequest(HttpRequest request) {
    final ip = request.connectionInfo?.remoteAddress.address ?? 'Desconocida';
    debugPrint("Victim connected from IP: $ip");
    if (onVictimConnected != null) {
      onVictimConnected!(ip);
    }
    // Generate the HTML response
    final String htmlResponse = '''
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Red de Rescate - ResQRadar</title>
    <style>
        body {
            font-family: 'Roboto', Arial, sans-serif;
            background-color: #121212;
            color: #FFFFFF;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            padding: 20px;
            text-align: center;
        }
        .container {
            background-color: #1E1E1E;
            border: 2px solid #00FFFF;
            border-radius: 16px;
            padding: 30px;
            max-width: 90%;
            width: 400px;
            box-shadow: 0 4px 15px rgba(0, 255, 255, 0.2);
        }
        h1 {
            color: #00FFFF;
            margin-bottom: 10px;
        }
        .alert {
            color: #FF3B30;
            font-size: 24px;
            font-weight: bold;
            margin: 20px 0;
            text-transform: uppercase;
            animation: pulse 2s infinite;
        }
        p {
            font-size: 18px;
            line-height: 1.5;
            color: #E0E0E0;
            margin-bottom: 20px;
        }
        .instructions {
            background-color: #2A2A2A;
            padding: 15px;
            border-radius: 8px;
            font-size: 16px;
            color: #B0B0B0;
        }
        @keyframes pulse {
            0% { transform: scale(1); }
            50% { transform: scale(1.05); }
            100% { transform: scale(1); }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>RESQRADAR TÁCTICO</h1>
        <div class="alert">¡MANTÉN LA CALMA!</div>
        <p>Estás conectado a la red de rescate oficial.</p>
        <p>Sabemos que estás ahí. Nuestros equipos y sensores te han detectado y estamos trabajando en la zona para sacarte.</p>
        <div class="instructions">
            <strong>Instrucciones:</strong><br><br>
            1. Trata de no moverte demasiado para evitar colapsos.<br>
            2. Conserva la batería de tu teléfono, apaga la pantalla si es posible.<br>
            3. Haz ruido (golpes secos) si escuchas a los rescatistas cerca.
        </div>
    </div>
</body>
</html>
''';

    final HttpResponse response = request.response;
    response
      ..headers.contentType = ContentType("text", "html", charset: "utf-8")
      // These headers are standard for forcing captive portals to not cache
      ..headers.add("Cache-Control", "no-store, no-cache, must-revalidate")
      ..headers.add("Pragma", "no-cache")
      ..headers.add("Expires", "0")
      ..write(htmlResponse);
    response.close();
  }

  Future<void> stop() async {
    if (_server != null) {
      await _server!.close(force: true);
      _server = null;
      debugPrint("Captive Portal Server stopped.");
    }
  }
}
