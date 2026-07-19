import 'dart:math';
import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';

import 'package:flutter/foundation.dart'; // Añadido para compute
import 'package:flutter/services.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_database/firebase_database.dart';
import 'firebase_options.dart';
import 'services/database_service.dart';
import 'services/ai_service.dart';
import 'screens/settings_screen.dart';
import 'screens/history_screen.dart';
import 'screens/splash_screen.dart';
import 'screens/swarm_screen.dart';
import 'config/app_config.dart';
import 'package:dart_ping/dart_ping.dart';
import 'package:flutter_compass/flutter_compass.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:network_info_plus/network_info_plus.dart';
import 'widgets/radar_painter.dart';
import 'ui/radar_screen.dart';
import 'dsp/multi_victim_tracker.dart';
import 'dsp/vital_signal_detector.dart';
import 'services/wifi_scanner_flutter.dart';
import 'services/captive_portal_server.dart';
import 'services/sensor_service.dart';
import 'services/kml_export_service.dart';
import 'services/ai_data_collector.dart';
import 'ui/map_screen.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/generated/app_localizations.dart';
final ValueNotifier<ThemeMode> themeNotifier = ValueNotifier(ThemeMode.dark);

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
  } catch (e, st) {
    debugPrint('Firebase opcional no inicializado: $e');
    debugPrint('$st');
  }
  try {
    await WakelockPlus.enable();
  } catch (_) {}
  runApp(const ResQRadarApp());
}

class ResQRadarApp extends StatelessWidget {
  const ResQRadarApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<ThemeMode>(
      valueListenable: themeNotifier,
      builder: (_, ThemeMode currentMode, __) {
        return MaterialApp(
          title: 'ResQRadar',
          debugShowCheckedModeBanner: false,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          localeResolutionCallback: (deviceLocale, supportedLocales) {
            if (deviceLocale != null) {
              for (var locale in supportedLocales) {
                if (locale.languageCode == deviceLocale.languageCode) {
                  return locale;
                }
              }
            }
            return const Locale('en'); // Default fallback to English globally
          },
          themeMode: currentMode,
          theme: ThemeData(
            brightness: Brightness.light,
            scaffoldBackgroundColor: const Color(0xFFF5F7FA), // Light gray/white background
            primaryColor: const Color(0xFF007BFF), // Medical Blue
            colorScheme: const ColorScheme.light(
              primary: Color(0xFF007BFF),
              secondary: Color(0xFFFF3B30), // Alert Red
              surface: Colors.white,
              onPrimary: Colors.white,
              onSecondary: Colors.white,
              onSurface: Color(0xFF333333),
            ),
            appBarTheme: const AppBarTheme(
              backgroundColor: Colors.white,
              elevation: 0,
              iconTheme: IconThemeData(color: Color(0xFF007BFF)),
              titleTextStyle: TextStyle(
                color: Color(0xFF333333),
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            fontFamily: 'Roboto', // Modern, clean font
            useMaterial3: true,
          ),
          darkTheme: ThemeData(
            brightness: Brightness.dark,
            scaffoldBackgroundColor: const Color(0xFF121212), // Fondo Táctico
            primaryColor: const Color(0xFFFF3B30), // Rojo Alerta
            colorScheme: const ColorScheme.dark(
              primary: Color(0xFFFF3B30),
              secondary: Color(0xFF007BFF),
              surface: Color(0xFF1E1E1E),
            ),
            appBarTheme: const AppBarTheme(
              backgroundColor: Color(0xFF1E1E1E),
              elevation: 0,
              iconTheme: IconThemeData(color: Color(0xFFFF3B30)),
              titleTextStyle: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.w600,
              ),
            ),
            fontFamily: 'Roboto',
            useMaterial3: true,
          ),
          home: const SplashScreen(),
        );
      },
    );
  }
}

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> with SingleTickerProviderStateMixin {
  // Conexión nativa con el hardware Android (Kotlin)
  static const EventChannel _sensorChannel = EventChannel('com.solvitco.ResQRadar/wifi_events');
  static const MethodChannel _methodChannel = MethodChannel('com.solvitco.ResQRadar/wifi');
  StreamSubscription? _sensorSubscription;
  
  final AiInferenceService _aiService = AiInferenceService();
  final DatabaseService _dbService = DatabaseService();
  late final CaptivePortalServer _portalServer = CaptivePortalServer(
    onVictimConnected: (ip) {
      if (mounted) {
        if (!_foundIPs.contains(ip)) {
          setState(() {
            _foundIPs.add(ip);
            _networkDevices = _foundIPs.length;
          });
        }
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.msgVictimPortalConnected(ip)),
            backgroundColor: Colors.redAccent,
            duration: const Duration(seconds: 5),
          ),
        );
      }
    },
  );

  bool _isScanning = false;
  String _status = "Radar Off";
  Color _statusColor = Colors.grey;
  double _currentRssi = 0.0;
  
  // Variables 3D Radar
  final MultiVictimTracker _tracker = MultiVictimTracker(numBins: 36, decay: 0.998);
  final RubbleConfidenceTracker _rubbleTracker = RubbleConfidenceTracker();
  final SensorService _sensorService = SensorService();
  final AiDataCollector _aiDataCollector = AiDataCollector();
  StreamSubscription<double>? _rssiSubscription;
  List<double> _rssiBuffer = [];
  List<TrappedVictim> _victims = [];
  List<HeatPoint> _heatmap = [];
  int? _selectedVictimId;
  
  // Lista de personas detectadas en tiempo real (en pantalla)
  List<DetectedPerson> _detectionsList = [];
  
  // Registro persistente de todas las víctimas encontradas en la sesión (para la base de datos)
  final Map<String, TrappedVictim> _allSessionVictims = {};
  
  late AnimationController _radarController;
  
  int _detections = 0;
  DateTime? _scanStartTime;

  double _compassHeading = 0.0;
  StreamSubscription? _compassSubscription;
  double _previousRssi = 0.0;
  final AudioPlayer _audioPlayer = AudioPlayer();
  DateTime? _lastMovementSoundTime;
  DateTime _lastAnalysisTime = DateTime.now();
  
  int _networkDevices = 0;
  bool _isNetworkScanning = false;
  List<String> _foundIPs = [];
  
  // Isolate y Calibración
  bool _isComputing = false;
  bool _isCalibrating = false;
  double? _baselineNoiseFloor;
  List<double> _calibrationBuffer = [];

  Future<void> _startCalibration() async {
    if (_isScanning) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(AppLocalizations.of(context)!.msgStopScanCalibrate)));
      return;
    }
    setState(() {
      _isCalibrating = true;
      _status = "Calibrating...";
      _statusColor = Colors.orange;
      _calibrationBuffer.clear();
    });
    
    await WifiScannerFlutter.startScanner();
    StreamSubscription? calibSub;
    calibSub = WifiScannerFlutter.rssiStream.listen((rssi) {
      _calibrationBuffer.add(rssi);
    });

    await Future.delayed(const Duration(seconds: 8)); // 8 segundos de muestreo
    calibSub.cancel();
    await WifiScannerFlutter.stopScanner();
    
    if (_calibrationBuffer.length > 20) {
      double mean = _calibrationBuffer.reduce((a, b) => a + b) / _calibrationBuffer.length;
      double variance = _calibrationBuffer.map((x) => pow(x - mean, 2)).reduce((a, b) => a + b) / _calibrationBuffer.length;
      _baselineNoiseFloor = sqrt(variance);
    }
    
    setState(() {
      _isCalibrating = false;
      _status = "Calibration Complete";
      _statusColor = Colors.green;
    });
    
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(AppLocalizations.of(context)!.msgNoiseFloor(_baselineNoiseFloor?.toStringAsFixed(2) ?? '0.00')),
        backgroundColor: Colors.green,
      ));
    }
  }

  Future<void> _scanNetworkSubnet() async {
    if (_isNetworkScanning) return;
    
    setState(() {
      _isNetworkScanning = true;
      _networkDevices = 1; 
      _foundIPs = [];
    });

    try {
      final info = NetworkInfo();
      final String? wifiIP = await info.getWifiIP();
      
      if (wifiIP != null) {
        if (!_foundIPs.contains(wifiIP)) {
          _foundIPs.add(wifiIP); // The device itself
        }
        
        final String subnet = wifiIP.substring(0, wifiIP.lastIndexOf('.'));
        // Dividimos en bloques pequeños para no colapsar la memoria del celular
        for (int i = 1; i <= 254; i += 15) {
          List<Future<void>> chunk = [];
          for (int j = i; j < i + 15 && j <= 254; j++) {
            final String ipToPing = '$subnet.$j';
            if (_foundIPs.contains(ipToPing)) continue;
            
            chunk.add(() async {
              try {
                final ping = Ping(ipToPing, count: 1, timeout: 2);
                await for (final event in ping.stream) {
                  if (event.response?.time != null) {
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
          await Future.wait(chunk);
        }
        
        if (mounted) setState(() => _isNetworkScanning = false);
      } else {
        if (mounted) setState(() => _isNetworkScanning = false);
      }
    } catch (e) {
      if (mounted) setState(() => _isNetworkScanning = false);
    }
  }

  @override
  void initState() {
    super.initState();
    _aiService.loadModel();
    _portalServer.start();
    _radarController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    );
    
    // Escuchar conexiones del Portal Cautivo en la Nube
    final DatabaseReference victimsRef = FirebaseDatabase.instance.ref(AppConfig.firebaseVictimsPath);
    victimsRef.onChildAdded.listen((DatabaseEvent event) {
      if (event.snapshot.value != null) {
        final data = Map<String, dynamic>.from(event.snapshot.value as Map);
        final ip = data['ip']?.toString() ?? 'Desconocida';
        
        // Evitar procesar conexiones muy viejas (ej. de sesiones anteriores)
        final int timestamp = int.tryParse(data['timestamp']?.toString() ?? '0') ?? 0;
        final int now = DateTime.now().millisecondsSinceEpoch;
        if (now - timestamp < 300000) { // Solo si ocurrió en los últimos 5 minutos
          if (mounted) {
            if (!_foundIPs.contains(ip)) {
              setState(() {
                _foundIPs.add(ip);
                _networkDevices = _foundIPs.length;
              });
            }
            SystemSound.play(SystemSoundType.alert);
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(AppLocalizations.of(context)!.msgVictimPortalConnected(ip), style: const TextStyle(fontWeight: FontWeight.bold)),
                backgroundColor: Colors.redAccent,
                duration: const Duration(seconds: 5),
              ),
            );
          }
        }
      }
    });
  }

  void _toggleScan() {
    if (_isScanning) {
      _stopScan();
    } else {
      _startScan();
    }
  }

  void _startScan() async {
    setState(() {
      _isScanning = true;
      _status = "Searching...";
      _statusColor = Colors.green;
      _detections = 0;
      _detectionsList.clear();
      _scanStartTime = DateTime.now();
    });
    
    _scanNetworkSubnet(); // Escaneo real para saber cuántos dispositivos hay en el router
    
    _aiDataCollector.startNewSession(); // Inicia la recolección de datos para la IA
    
    bool started = await WifiScannerFlutter.startScanner();
    if (started) {
      _sensorService.startListening();
      _compassSubscription = FlutterCompass.events?.listen((event) {
        setState(() {
          _compassHeading = event.heading ?? 0.0;
        });
      });

      _radarController.repeat();

      _rssiSubscription = WifiScannerFlutter.rssiStream.listen((rssi) {
        _handleRssiData(rssi);
      });
    } else {
      debugPrint("Failed to start scanner from WifiScannerFlutter");
    }
  }

  void _handleRssiData(double rssi) async {
    _rssiBuffer.add(rssi);
    if (_rssiBuffer.length > 100) _rssiBuffer.removeAt(0);

    if (_previousRssi != 0.0 && (rssi - _previousRssi).abs() > 15) {
      if (_lastMovementSoundTime == null || DateTime.now().difference(_lastMovementSoundTime!).inSeconds > 5) {
        _lastMovementSoundTime = DateTime.now();
        
        // Simular un fuerte 'Ping' de radar con vibración militar y sonido de click del sistema
        HapticFeedback.heavyImpact();
        SystemSound.play(SystemSoundType.click);
        Future.delayed(const Duration(milliseconds: 200), () {
          HapticFeedback.heavyImpact();
          SystemSound.play(SystemSoundType.click);
        });
        Future.delayed(const Duration(milliseconds: 400), () {
          HapticFeedback.vibrate();
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.msgMovementDetected, style: const TextStyle(fontWeight: FontWeight.bold)),
              backgroundColor: Colors.red,
              duration: Duration(seconds: 2),
            ),
          );
        }
      }
    }

    setState(() {
      _previousRssi = _currentRssi;
      _currentRssi = rssi;
    });

    if (_isCalibrating) return; // No analizar mientras se calibra

    // THROTTLE: Solo correr el DSP cada 400ms (igual que la app original)
    // para evitar que el radar decaiga demasiado rápido y borre los puntos al rotar.
    if (_rssiBuffer.length >= 20 && !_isComputing) {
      if (DateTime.now().difference(_lastAnalysisTime).inMilliseconds < 400) return;
      _lastAnalysisTime = DateTime.now();
      _isComputing = true;
      List<double> signalCopy = List.from(_rssiBuffer);
      
      compute(VitalSignalDetector.analyzeCompute, DetectionArgs(
        signal: signalCopy,
        fs: 20.0,
        sensitivity: 1.5,
        rubbleMode: true,
        baselineNoiseFloor: _baselineNoiseFloor,
        deviceMotion: _sensorService.motionNoise,
      )).then((result) {
        if (!mounted) return;
        
        int nowMs = DateTime.now().millisecondsSinceEpoch;
        _rubbleTracker.update(result, nowMs);

        // Calculate rssi correctly (since this is an async callback, _currentRssi might have changed slightly, but we use the passed 'rssi')
        double estimatedDistance = pow(10.0, (-40.0 - rssi) / 25.0).toDouble().clamp(0.1, 10.0);
        double strength = ((rssi + 100) / 70.0).clamp(0.0, 1.0);

        _tracker.update(
          bearingDeg: _compassHeading,
          distanceM: estimatedDistance,
          strength: strength,
          detection: result,
          rubbleConf: _rubbleTracker.score,
          proximity: result.hint,
          nowMs: nowMs,
        );

        setState(() {
          _victims = _tracker.locateVictims(nowMs);
          _heatmap = _tracker.heatmapPoints();
          
          _detectionsList = _victims.map((v) => DetectedPerson(
            id: v.label,
            distance: v.distance,
            bpm: v.heartRateBpm,
            rpm: v.respRate,
            isCritical: v.status.contains("Crítica") || v.status.contains("débil"),
            angle: v.bearing * (pi / 180.0),
          )).toList();

          // Guardar o actualizar en el registro persistente de la sesión
          for (var v in _victims) {
            _allSessionVictims[v.label] = v;
          }

          _detections = _detectionsList.length;

          if (_victims.isNotEmpty) {
             _status = "ALERTA VITAL";
             _statusColor = Colors.red;
             // Sonido continuo de vida detectada
             if (_lastMovementSoundTime == null || DateTime.now().difference(_lastMovementSoundTime!).inSeconds > 2) {
               _lastMovementSoundTime = DateTime.now();
               HapticFeedback.heavyImpact(); // Vibración pesada táctica
             }
             
             // Minar datos positivos (Vida detectada)
             final bestVictim = _victims.reduce((a, b) => a.confidence > b.confidence ? a : b);
             _aiDataCollector.logDataRow(
               rssiBuffer: signalCopy,
               deviceMotion: _sensorService.motionNoise,
               detectionLabel: bestVictim.status,
               confidence: bestVictim.confidence,
             );
          } else {
             _status = "Escaneando 360°...";
             _statusColor = Colors.green;
             
             // Minar datos negativos (Ruido/Piedras)
             _aiDataCollector.logDataRow(
               rssiBuffer: signalCopy,
               deviceMotion: _sensorService.motionNoise,
               detectionLabel: 'Ruido/Escombros',
               confidence: 0.0,
             );
          }
        });
        
        _isComputing = false;
      }).catchError((e) {
        _isComputing = false;
        debugPrint("Error en Isolate: \$e");
      });
    }
  }

  void _stopScan() async {
    setState(() {
      _isScanning = false;
      _status = "Radar Off";
      _statusColor = Colors.grey;
    });
    
    _compassSubscription?.cancel();
    _rssiSubscription?.cancel();
    _sensorService.stopListening();
    _aiDataCollector.stopSession();
    _radarController.stop();
    
    try {
      await WifiScannerFlutter.stopScanner();
    } catch (e) {
      debugPrint("Error stopping scanner: $e");
    }
    
    if (_scanStartTime != null && _detections > 0) {
      final duration = DateTime.now().difference(_scanStartTime!).inSeconds;
      String? victimsJson;
      try {
        final List<Map<String, dynamic>> victimData = _allSessionVictims.values.map((v) => {
          'id': v.label,
          'distance': v.distance,
          'bpm': v.heartRateBpm,
          'rpm': v.respRate,
          'isCritical': v.status.contains("Crítica") || v.status.contains("débil"),
        }).toList();
        victimsJson = jsonEncode(victimData);
      } catch (e) {
        debugPrint("Error encoding victims JSON: $e");
      }
      // Usamos _allSessionVictims.length para que el número de detecciones coincida con las únicas encontradas
      await _dbService.saveSession(duration, _allSessionVictims.length, victimsJson: victimsJson);
    }

    setState(() {
      _isScanning = false;
      _status = "Radar Off";
      _statusColor = Colors.grey;
      _currentRssi = 0.0;
      _detectionsList.clear();
      _allSessionVictims.clear();
    });
  }

  @override
  void dispose() {
    _portalServer.stop();
    _sensorSubscription?.cancel();
    _compassSubscription?.cancel();
    _radarController.dispose();
    _audioPlayer.dispose();
    super.dispose();
  }

  void _showAboutApp() {
    showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          backgroundColor: const Color(0xFF1E1E1E),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
          title: Row(
            children: [
              const Icon(Icons.info_outline, color: Colors.blueAccent, size: 30),
              const SizedBox(width: 10),
              Text(AppLocalizations.of(context)!.aboutTitle, style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(AppLocalizations.of(context)!.appTitle, style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.bold)),
              SizedBox(height: 5),
              Text(AppLocalizations.of(context)!.aboutVersion, style: const TextStyle(color: Colors.grey, fontSize: 14)),
              Divider(color: Colors.grey),
              SizedBox(height: 10),
              Text(AppLocalizations.of(context)!.aboutDeveloper, style: const TextStyle(color: Colors.grey, fontSize: 12)),
              Text("Moovitya, C.A", style: TextStyle(color: Colors.white, fontSize: 16)),
              SizedBox(height: 10),
              Text(AppLocalizations.of(context)!.aboutWeb, style: const TextStyle(color: Colors.grey, fontSize: 12)),
              Text("moovitya.com", style: TextStyle(color: Colors.blueAccent, fontSize: 16)),
              SizedBox(height: 10),
              Text(AppLocalizations.of(context)!.aboutContact, style: const TextStyle(color: Colors.grey, fontSize: 12)),
              Text("+584242304352", style: TextStyle(color: Colors.green, fontSize: 16, fontWeight: FontWeight.bold)),
              SizedBox(height: 10),
              Text(AppLocalizations.of(context)!.aboutLicense, style: const TextStyle(color: Colors.grey, fontSize: 12)),
              Text(AppLocalizations.of(context)!.aboutLicenseDesc, style: const TextStyle(color: Colors.white, fontSize: 14)),
              SizedBox(height: 15),
              Divider(color: Colors.grey),
              SizedBox(height: 5),
              Text(AppLocalizations.of(context)!.aboutCredits, style: const TextStyle(color: Colors.grey, fontSize: 12)),
              Text(AppLocalizations.of(context)!.aboutCreditsDesc, style: const TextStyle(color: Colors.white70, fontSize: 12, fontStyle: FontStyle.italic)),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(AppLocalizations.of(context)!.btnClose, style: TextStyle(color: Colors.blueAccent)),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.appTitle),
        leading: IconButton(
          icon: const Icon(Icons.history),
          onPressed: () {
            Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const HistoryScreen()),
            );
          },
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.tune), // Icono de calibración
            tooltip: "Calibrar Entorno",
            onPressed: _isScanning ? null : _startCalibration,
          ),
          IconButton(
            icon: const Icon(Icons.wifi_tethering),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SwarmScreen()),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.settings_outlined),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => const SettingsScreen()),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.map),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(builder: (context) => MapScreen(victims: _allSessionVictims.values.toList())),
              );
            },
          ),
          IconButton(
            icon: const Icon(Icons.info_outline),
            tooltip: "Acerca de",
            onPressed: _showAboutApp,
          )
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            const SizedBox(height: 20),
            // Header stats
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(child: _buildStatCard('Estado IA', _status, _statusColor, width: null)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: GestureDetector(
                      onTap: () {
                        if (_foundIPs.isNotEmpty) {
                          showDialog(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: Text(AppLocalizations.of(context)!.titleConnectedDevices),
                              content: Column(
                                mainAxisSize: MainAxisSize.min,
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: _foundIPs.map((ip) => Text(ip)).toList(),
                              ),
                              actions: [
                                TextButton(onPressed: () => Navigator.pop(context), child: Text(AppLocalizations.of(context)!.btnClose.toUpperCase()))
                              ],
                            ),
                          );
                        }
                      },
                      child: _buildStatCard('Dispositivos', '$_networkDevices', Colors.orange, width: null),
                    )
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: _buildStatCard('RSSI', '${_currentRssi.toStringAsFixed(0)} dBm', Colors.blue, width: null)),
                ],
              ),
            ),
            const SizedBox(height: 10),
            // Removed FFT Stats Row because they are now in the Victim Card
            const SizedBox(height: 20),
            // Radar Section
            Expanded(
              child: Container(
                margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                child: AnimatedBuilder(
                  animation: _radarController,
                  builder: (context, child) {
                    return CustomPaint(
                      painter: RadarPainter(
                        scanAngle: _radarController.value * 2 * pi,
                        detections: _detectionsList,
                        baseColor: const Color(0xFF00FFFF),
                      ),
                      child: Container(),
                    );
                  }
                ),
              ),
            ),
            


            // Lista de Víctimas Detectadas
            if (_detectionsList.isNotEmpty)
              Container(
                height: 120,
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  itemCount: _detectionsList.length,
                  itemBuilder: (context, index) {
                    final p = _detectionsList[index];
                    return VictimCard(person: p);
                  },
                ),
              ),
            // Brújula Táctica
            if (_isScanning)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 10),
                child: Column(
                  children: [
                    Text(
                      "${_compassHeading.toStringAsFixed(0)}\u00B0",
                      style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.blueGrey),
                    ),
                    const SizedBox(height: 5),
                    Transform.rotate(
                      angle: (_compassHeading * (pi / 180) * -1),
                      child: Icon(Icons.navigation, size: 40, color: Theme.of(context).primaryColor),
                    ),
                  ],
                ),
              ),
              
            const SizedBox(height: 10),
            // Bottom Action
            Padding(
              padding: const EdgeInsets.all(20.0),
              child: ElevatedButton(
                onPressed: _toggleScan,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _isScanning ? Colors.red : Colors.green,
                  foregroundColor: Colors.white,
                  minimumSize: const Size(double.infinity, 56),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  elevation: 0,
                ),
                child: Text(
                  _isScanning ? AppLocalizations.of(context)!.btnStopScan : AppLocalizations.of(context)!.btnStartScan, 
                  style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)
                ),
              ),
            )
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard(String title, String value, Color color, {double? width}) {
    return Container(
      width: width,
      padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withAlpha(50), width: 1.5),
        boxShadow: [
          BoxShadow(
            color: color.withAlpha(15),
            blurRadius: 10,
            offset: const Offset(0, 4),
          )
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text(
            title, 
            style: const TextStyle(color: Colors.grey, fontSize: 11, fontWeight: FontWeight.w600),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 6),
          SizedBox(
            height: 24, // Fija altura para evitar saltos raros
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.center,
              child: Text(
                value, 
                style: TextStyle(color: color, fontSize: 16, fontWeight: FontWeight.w800, letterSpacing: 0.5),
                textAlign: TextAlign.center,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class VictimCard extends StatefulWidget {
  final DetectedPerson person;
  const VictimCard({super.key, required this.person});

  @override
  State<VictimCard> createState() => _VictimCardState();
}

class _VictimCardState extends State<VictimCard> with TickerProviderStateMixin {
  late AnimationController _heartController;
  late AnimationController _lungController;

  @override
  void initState() {
    super.initState();
    
    // Controlador del corazón (Palpitación rápida)
    // 60 BPM = 1 latido por segundo (1000ms)
    int heartDurationMs = widget.person.bpm > 0 ? (60000 ~/ widget.person.bpm) : 1000;
    _heartController = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: heartDurationMs ~/ 2), // Mitad para subir, mitad para bajar
    )..repeat(reverse: true);

    // Controlador de pulmones (Respiración lenta)
    // 15 RPM = 1 respiración cada 4 segundos (4000ms)
    int lungDurationMs = widget.person.rpm > 0 ? (60000 ~/ widget.person.rpm) : 4000;
    _lungController = AnimationController(
      vsync: this,
      duration: Duration(milliseconds: lungDurationMs ~/ 2),
    )..repeat(reverse: true);
  }
  
  @override
  void didUpdateWidget(VictimCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.person.bpm != widget.person.bpm) {
      int ms = widget.person.bpm > 0 ? (60000 ~/ widget.person.bpm) : 1000;
      _heartController.duration = Duration(milliseconds: ms ~/ 2);
      _heartController.repeat(reverse: true);
    }
    if (oldWidget.person.rpm != widget.person.rpm) {
      int ms = widget.person.rpm > 0 ? (60000 ~/ widget.person.rpm) : 4000;
      _lungController.duration = Duration(milliseconds: ms ~/ 2);
      _lungController.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _heartController.dispose();
    _lungController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        showDialog(
          context: context,
          builder: (context) {
            return AlertDialog(
              backgroundColor: const Color(0xFF282433),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
              title: Text(
                AppLocalizations.of(context)!.victimModalTitle(widget.person.id),
                style: const TextStyle(
                  color: Color(0xFFF24E6B),
                  fontWeight: FontWeight.bold,
                  fontSize: 22,
                  height: 1.3,
                ),
              ),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(AppLocalizations.of(context)!.victimModalLocalData, style: const TextStyle(color: Colors.greenAccent, fontWeight: FontWeight.bold, fontSize: 13)),
                  const SizedBox(height: 16),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalCoordX, '${(widget.person.distance * cos(widget.person.angle)).toStringAsFixed(2)} m'),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalCoordY, '${(widget.person.distance * sin(widget.person.angle)).toStringAsFixed(2)} m'),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalDepth, AppLocalizations.of(context)!.victimModalDepthValue),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalHeading, '${(widget.person.angle * 180.0 / pi).toStringAsFixed(0)}°'),
                  _buildTextRow(AppLocalizations.of(context)!.lblDistance, '${widget.person.distance.toStringAsFixed(2)} m'),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalProximity, widget.person.distance < 2.0 ? AppLocalizations.of(context)!.victimModalProxClose : AppLocalizations.of(context)!.victimModalProxFar),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalBreathing, AppLocalizations.of(context)!.victimModalBreathingYes(widget.person.rpm.toStringAsFixed(1))),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalHeart, AppLocalizations.of(context)!.victimModalHeartYes(widget.person.bpm.toStringAsFixed(0))),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalActivity, AppLocalizations.of(context)!.victimModalActivityYes),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalConfVital, '100%'),
                  _buildTextRow(AppLocalizations.of(context)!.victimModalConfDebris, '100%'),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: Text(AppLocalizations.of(context)!.btnClose, style: TextStyle(color: Color(0xFFC393D8), fontSize: 16)),
                ),
              ],
            );
          },
        );
      },
      child: Container(
        width: 220, // Siempre expandido
        margin: const EdgeInsets.only(right: 8),
        padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: widget.person.isCritical ? Colors.red : Colors.greenAccent,
          width: 2,
        ),
        boxShadow: [
          BoxShadow(
            color: widget.person.isCritical ? Colors.red.withOpacity(0.2) : Colors.black.withOpacity(0.05),
            blurRadius: 10,
          )
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                widget.person.id.length > 15 
                    ? "${widget.person.id.substring(0, 15)}..." 
                    : widget.person.id,
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 12,
                  color: widget.person.isCritical ? Colors.red : null,
                ),
              ),
              Padding(
                padding: const EdgeInsets.only(left: 4.0),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.blue.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.my_location, color: Colors.blue, size: 12),
                      const SizedBox(width: 4),
                      Text(
                        '${widget.person.distance.toStringAsFixed(1)} m',
                        style: const TextStyle(color: Colors.blue, fontWeight: FontWeight.bold, fontSize: 11),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              // Animación del Corazón
              ScaleTransition(
                scale: Tween<double>(begin: 0.8, end: 1.2).animate(CurvedAnimation(parent: _heartController, curve: Curves.easeInOut)),
                child: Icon(Icons.favorite, color: widget.person.isCritical ? Colors.red : Colors.green, size: 16),
              ),
              const SizedBox(width: 4),
              Text('${widget.person.bpm.toStringAsFixed(0)} ${AppLocalizations.of(context)!.lblBPM}', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
              
              const SizedBox(width: 12),
              
              // Animación de Pulmones
              ScaleTransition(
                scale: Tween<double>(begin: 0.9, end: 1.1).animate(CurvedAnimation(parent: _lungController, curve: Curves.easeInOutSine)),
                child: Icon(Icons.air, color: widget.person.isCritical ? Colors.red : Colors.blue, size: 16),
              ),
              const SizedBox(width: 4),
              Text('${widget.person.rpm.toStringAsFixed(0)} ${AppLocalizations.of(context)!.lblRPM}', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
            ],
          ),
        ],
      ),
      ),
    );
  }

  Widget _buildTextRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey, fontSize: 14)),
          Text(value, style: const TextStyle(color: Colors.white, fontSize: 14, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}
