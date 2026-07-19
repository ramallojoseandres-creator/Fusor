import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_compass/flutter_compass.dart';
import '../services/wifi_scanner_flutter.dart';
import '../dsp/vital_signal_detector.dart';
import '../dsp/multi_victim_tracker.dart';
import 'package:dart_ping/dart_ping.dart';
import 'package:network_info_plus/network_info_plus.dart';
import '../widgets/radar_painter.dart';

class RadarScreen extends StatefulWidget {
  const RadarScreen({Key? key}) : super(key: key);

  @override
  _RadarScreenState createState() => _RadarScreenState();
}

class _RadarScreenState extends State<RadarScreen> with SingleTickerProviderStateMixin {
  late AnimationController _scanController;
  final MultiVictimTracker _tracker = MultiVictimTracker(numBins: 36, decay: 0.998);
  final RubbleConfidenceTracker _rubbleTracker = RubbleConfidenceTracker();
  
  StreamSubscription<double>? _rssiSubscription;
  StreamSubscription<CompassEvent>? _compassSubscription;
  
  List<double> _rssiBuffer = [];
  double _currentBearing = 0.0;
  
  List<TrappedVictim> _victims = [];
  List<HeatPoint> _heatmap = [];
  int? _selectedVictimId;
  
  bool _isScanning = false;
  String _statusMessage = "Listo para iniciar escaneo";
  int _networkDevices = 0;
  List<String> _foundIPs = [];

  Future<void> _scanNetworkSubnet() async {
    try {
      final info = NetworkInfo();
      final String? wifiIP = await info.getWifiIP();
      
      if (wifiIP != null) {
        if (!_foundIPs.contains(wifiIP)) {
          _foundIPs.add(wifiIP);
        }
        
        final String subnet = wifiIP.substring(0, wifiIP.lastIndexOf('.'));
        List<Future<void>> pingTasks = [];
        
        for (int i = 1; i <= 254; i++) {
          final String ipToPing = '$subnet.$i';
          if (_foundIPs.contains(ipToPing)) continue;
          
          pingTasks.add(() async {
            try {
              // Timeout to 2 seconds, 1 ping
              final ping = Ping(ipToPing, count: 1, timeout: 2);
              await for (final event in ping.stream) {
                if (event is PingResponse) {
                  if (mounted && !_foundIPs.contains(ipToPing)) {
                    setState(() {
                      _foundIPs.add(ipToPing);
                      _networkDevices = _foundIPs.length;
                    });
                  }
                  break; 
                }
              }
            } catch (_) { }
          }());
        }
        
        // Batch them in chunks of 15 to avoid overloading the Android process limit
        for (int i = 0; i < pingTasks.length; i += 15) {
          int end = (i + 15 < pingTasks.length) ? i + 15 : pingTasks.length;
          await Future.wait(pingTasks.sublist(i, end));
        }
      }
    } catch (e) { }
  }

  @override
  void initState() {
    super.initState();
    _scanController = AnimationController(vsync: this, duration: const Duration(seconds: 3))..repeat();
    _startRadar();
    _scanNetworkSubnet();
  }

  Future<void> _startRadar() async {
    bool started = await WifiScannerFlutter.startScanner();
    if (started) {
      setState(() {
        _isScanning = true;
        _statusMessage = "Escaneando en 360°...";
      });
      
      _compassSubscription = FlutterCompass.events?.listen((event) {
        if (event.heading != null) {
          setState(() {
            _currentBearing = event.heading!;
          });
        }
      });

      _rssiSubscription = WifiScannerFlutter.rssiStream.listen((rssi) {
        _handleRssiData(rssi);
      });
    } else {
      setState(() {
        _statusMessage = "Error al iniciar escáner Wi-Fi";
      });
    }
  }

  void _handleRssiData(double rssi) {
    _rssiBuffer.add(rssi);
    // Keep ~5 seconds of data at 20Hz (100 samples)
    if (_rssiBuffer.length > 100) {
      _rssiBuffer.removeAt(0);
    }

    if (_rssiBuffer.length >= 20) {
      // Analyze data
      DetectionResult result = VitalSignalDetector.analyze(
        signal: _rssiBuffer,
        fs: 20.0,
        sensitivity: 1.5,
        rubbleMode: true,
      );

      int nowMs = DateTime.now().millisecondsSinceEpoch;
      double rubbleConf = _rubbleTracker.update(result, nowMs);

      // Convert RSSI to a distance proxy (simple path loss model approximation)
      // RSSI = -10 * n * log10(d) + A -> d = 10 ^ ((A - RSSI) / (10 * n))
      // assuming A=-40, n=2.5
      double estimatedDistance = math.pow(10.0, (-40.0 - rssi) / 25.0).toDouble().clamp(0.1, 10.0);
      
      // Signal strength from 0 to 1 for heatmap
      double strength = ((rssi + 100) / 70.0).clamp(0.0, 1.0);

      _tracker.update(
        bearingDeg: _currentBearing,
        distanceM: estimatedDistance,
        strength: strength,
        detection: result,
        rubbleConf: rubbleConf,
        proximity: result.hint, // Use hint for proximity for now
        nowMs: nowMs,
      );

      setState(() {
        _statusMessage = result.status;
        _victims = _tracker.locateVictims(nowMs);
        _heatmap = _tracker.heatmapPoints();
        
        // Add to trail
        double rad = (90 - _currentBearing) * math.pi / 180.0;
        double rx = estimatedDistance * math.cos(rad);
        double ry = estimatedDistance * math.sin(rad);
      });
    }
  }

  @override
  void dispose() {
    _scanController.dispose();
    _rssiSubscription?.cancel();
    _compassSubscription?.cancel();
    WifiScannerFlutter.stopScanner();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Radar de Rescate 3D'),
        backgroundColor: const Color(0xFF0D140D),
        foregroundColor: const Color(0xFF00FFFF),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8.0),
            child: ActionChip(
              backgroundColor: const Color(0xFF1A1A1A),
              side: const BorderSide(color: Color(0xFF00FFFF)),
              label: Text(
                'Dispositivos: $_networkDevices',
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
              ),
              onPressed: () {
                showDialog(
                  context: context,
                  builder: (context) {
                    return AlertDialog(
                      backgroundColor: const Color(0xFF1A1A1A),
                      title: const Text('Dispositivos Conectados (IPs)', style: TextStyle(color: Colors.white)),
                      content: SizedBox(
                        width: double.maxFinite,
                        child: ListView.builder(
                          shrinkWrap: true,
                          itemCount: _foundIPs.length,
                          itemBuilder: (context, index) {
                            return ListTile(
                              leading: const Icon(Icons.wifi, color: Colors.greenAccent),
                              title: Text(_foundIPs[index], style: const TextStyle(color: Colors.white70, fontSize: 16)),
                            );
                          },
                        ),
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: const Text('Cerrar', style: TextStyle(color: Color(0xFF00FFFF))),
                        ),
                      ],
                    );
                  },
                );
              },
            ),
          )
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text(
              _statusMessage,
              style: const TextStyle(color: Colors.greenAccent, fontSize: 16),
              textAlign: TextAlign.center,
            ),
          ),
          Expanded(
            child: AnimatedBuilder(
              animation: _scanController,
              builder: (context, child) {
                return CustomPaint(
                  painter: RadarPainter(
                    scanAngle: _scanController.value * 2 * math.pi,
                    detections: _victims.map((v) => DetectedPerson(
                      id: v.label,
                      distance: v.distance,
                      angle: (v.bearing - 90) * math.pi / 180.0, // Convert to drawing angle
                      bpm: v.heartRateBpm,
                      rpm: v.respRate,
                      isCritical: (v.heartRateBpm > 0 && v.heartRateBpm < 45) || (v.respRate > 0 && v.respRate < 8),
                    )).toList(),
                    baseColor: const Color(0xFF00FFFF),
                  ),
                  child: Container(),
                );
              },
            ),
          ),
          if (_victims.isNotEmpty)
            Container(
              height: 160,
              color: const Color(0xFF1A1A1A),
              child: ListView.builder(
                scrollDirection: Axis.horizontal,
                itemCount: _victims.length,
                itemBuilder: (context, index) {
                  final v = _victims[index];
                  bool isSelected = v.id == _selectedVictimId;
                  double currentRssi = _rssiBuffer.isNotEmpty ? _rssiBuffer.last : 0.0;
                  bool isCritical = (v.heartRateBpm > 0 && v.heartRateBpm < 45) || (v.respRate > 0 && v.respRate < 8);
                  
                  return GestureDetector(
                    onTap: () => setState(() => _selectedVictimId = v.id),
                    child: Container(
                      width: 260,
                      margin: const EdgeInsets.all(8),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: isSelected ? const Color(0xFF333333) : const Color(0xFF222222),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: isCritical 
                              ? Colors.red 
                              : (isSelected ? const Color(0xFFFFCC00) : Colors.transparent),
                          width: isCritical ? 2.0 : 1.0,
                        ),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(v.label, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13)),
                          Text("Dist: ${v.distance.toStringAsFixed(1)}m | Señal RSSI: ${currentRssi.toStringAsFixed(1)} dBm", style: const TextStyle(color: Colors.white70, fontSize: 11)),
                          Text("Estado IA: ${v.status}", style: TextStyle(color: v.heartBeating ? Colors.green : Colors.orange, fontSize: 11), maxLines: 1, overflow: TextOverflow.ellipsis),
                          const SizedBox(height: 4),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  PulsingIcon(icon: Icons.favorite, color: Colors.redAccent, bpm: v.heartRateBpm),
                                  const SizedBox(width: 4),
                                  Text("BPM: ${v.heartRateBpm.toStringAsFixed(1)}", style: const TextStyle(color: Colors.redAccent, fontSize: 12, fontWeight: FontWeight.bold)),
                                ],
                              ),
                              Row(
                                children: [
                                  PulsingIcon(icon: Icons.air, color: Colors.lightBlueAccent, bpm: v.respRate),
                                  const SizedBox(width: 4),
                                  Text("RPM: ${v.respRate.toStringAsFixed(1)}", style: const TextStyle(color: Colors.lightBlueAccent, fontSize: 12, fontWeight: FontWeight.bold)),
                                ],
                              ),
                            ],
                          ),
                          if (isCritical)
                            Container(
                              margin: const EdgeInsets.only(top: 4),
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(color: Colors.red, borderRadius: BorderRadius.circular(4)),
                              child: const Text("ALERTA CRÍTICA", style: TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold)),
                            ),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class PulsingIcon extends StatefulWidget {
  final IconData icon;
  final Color color;
  final double bpm;

  const PulsingIcon({Key? key, required this.icon, required this.color, required this.bpm}) : super(key: key);

  @override
  _PulsingIconState createState() => _PulsingIconState();
}

class _PulsingIconState extends State<PulsingIcon> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(seconds: 1));
    _updateSpeed();
  }

  @override
  void didUpdateWidget(PulsingIcon oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.bpm != widget.bpm) {
      _updateSpeed();
    }
  }

  void _updateSpeed() {
    if (widget.bpm <= 0) {
      _controller.stop();
    } else {
      int durationMs = (60000 / widget.bpm / 2).clamp(100, 2000).toInt();
      _controller.duration = Duration(milliseconds: durationMs);
      _controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(
      scale: Tween(begin: 0.8, end: 1.2).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut)),
      child: Icon(widget.icon, color: widget.color, size: 14),
    );
  }
}
