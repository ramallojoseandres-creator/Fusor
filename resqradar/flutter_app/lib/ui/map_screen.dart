import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'dart:io';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';
import 'package:path_provider/path_provider.dart';
import 'package:flutter_map_cache/flutter_map_cache.dart';
import 'package:dio_cache_interceptor/dio_cache_interceptor.dart';
import 'package:dio_cache_interceptor_hive_store/dio_cache_interceptor_hive_store.dart';
import '../config/app_config.dart';
import '../dsp/multi_victim_tracker.dart';
import '../services/kml_export_service.dart';

class MapScreen extends StatefulWidget {
  final List<TrappedVictim> victims;
  
  const MapScreen({super.key, required this.victims});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  LatLng? _currentPosition;
  bool _isLoading = true;
  final MapController _mapController = MapController();
  CacheStore? _cacheStore;

  @override
  void initState() {
    super.initState();
    _initCacheStore();
    _determinePosition();
  }

  Future<void> _initCacheStore() async {
    final dir = await getApplicationDocumentsDirectory();
    final mapCachePath = '${dir.path}/map_cache';
    final directory = Directory(mapCachePath);
    if (!directory.existsSync()) {
      directory.createSync(recursive: true);
    }
    setState(() {
      _cacheStore = HiveCacheStore(
        mapCachePath,
        hiveBoxName: 'vitalfi_map_tiles',
      );
    });
  }

  Future<void> _determinePosition() async {
    bool serviceEnabled;
    LocationPermission permission;

    serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (mounted) setState(() => _isLoading = false);
      return;
    }

    permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        if (mounted) setState(() => _isLoading = false);
        return;
      }
    }
    
    if (permission == LocationPermission.deniedForever) {
      if (mounted) setState(() => _isLoading = false);
      return;
    } 

    Position position = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
    if (mounted) {
      setState(() {
        _currentPosition = LatLng(position.latitude, position.longitude);
        _isLoading = false;
      });
    }
  }

  LatLng _calculateDestination(LatLng start, double distanceMeters, double bearingDegrees) {
    const double radiusEarth = 6371000.0;
    
    final double distRatio = distanceMeters / radiusEarth;
    final double distRatioSine = math.sin(distRatio);
    final double distRatioCosine = math.cos(distRatio);
    
    final double startLatRad = _toRadians(start.latitude);
    final double startLonRad = _toRadians(start.longitude);
    
    final double startLatCos = math.cos(startLatRad);
    final double startLatSin = math.sin(startLatRad);
    
    final double endLatRads = math.asin(
        (startLatSin * distRatioCosine) +
        (startLatCos * distRatioSine * math.cos(_toRadians(bearingDegrees)))
    );

    final double endLonRads = startLonRad + math.atan2(
        math.sin(_toRadians(bearingDegrees)) * distRatioSine * startLatCos,
        distRatioCosine - startLatSin * math.sin(endLatRads)
    );

    return LatLng(_toDegrees(endLatRads), _toDegrees(endLonRads));
  }

  double _toRadians(double degrees) => degrees * math.pi / 180.0;
  double _toDegrees(double radians) => radians * 180.0 / math.pi;

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }
    
    if (_currentPosition == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Mapa Táctico')),
        body: const Center(child: Text('Ubicación GPS no disponible.\nAsegúrate de tener el GPS activo.', textAlign: TextAlign.center)),
      );
    }

    List<Marker> markers = [];
    List<Polyline> polylines = [];
    
    // Rescatista
    markers.add(
      Marker(
        point: _currentPosition!,
        width: 40,
        height: 40,
        child: const Icon(Icons.my_location, color: Colors.blue, size: 30),
      )
    );

    // Victimas
    for (var v in widget.victims) {
      LatLng victimPos = _calculateDestination(_currentPosition!, v.distance, v.bearing);
      
      markers.add(
        Marker(
          point: victimPos,
          width: 50,
          height: 60,
          child: Column(
            children: [
              Icon(
                Icons.person_pin, 
                color: (v.heartRateBpm > 100 || v.heartRateBpm < 50 || v.respRate > 25 || v.respRate < 8) ? Colors.red : Colors.greenAccent, 
                size: 30
              ),
              Container(
                color: Colors.black54,
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                child: Text(
                  v.label,
                  style: const TextStyle(color: Colors.white, fontSize: 10, fontWeight: FontWeight.bold),
                ),
              )
            ],
          ),
        )
      );
      
      polylines.add(
        Polyline(
          points: [_currentPosition!, victimPos],
          strokeWidth: 2.0,
          color: (v.heartRateBpm > 100 || v.heartRateBpm < 50 || v.respRate > 25 || v.respRate < 8) ? Colors.red.withOpacity(0.5) : Colors.greenAccent.withOpacity(0.5),
        )
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mapa Táctico'),
        actions: [
          IconButton(
            icon: const Icon(Icons.download),
            tooltip: "Exportar Mapa 3D (KML)",
            onPressed: () async {
              if (widget.victims.isEmpty || _currentPosition == null) {
                 ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No hay víctimas o GPS no está listo.')));
                 return;
              }
              List<Map<String, dynamic>> exportData = widget.victims.map((v) {
                 LatLng victimPos = _calculateDestination(_currentPosition!, v.distance, v.bearing);
                 return {
                   'lat': victimPos.latitude,
                   'lng': victimPos.longitude,
                   'bpm': v.heartRateBpm,
                   'rpm': v.respRate,
                   'isBreathing': v.status.contains('Crítica') || v.status.contains('débil'),
                   'confidence': v.confidence
                 };
              }).toList();
              await KmlExportService.exportVictimsToKml(exportData);
            },
          )
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            mapController: _mapController,
            options: MapOptions(
              initialCenter: _currentPosition!,
              initialZoom: 19.0, // High zoom for close range
              maxZoom: 22.0,
            ),
            children: [
              if (_cacheStore != null)
                TileLayer(
                  urlTemplate: AppConfig.mapTileUrl,
                  userAgentPackageName: 'com.solvitco.vitalfi',
                  tileProvider: CachedTileProvider(
                    store: _cacheStore!,
                    maxStale: const Duration(days: 90), // Guarda mapas por 90 días
                  ),
                )
              else
                TileLayer(
                  urlTemplate: AppConfig.mapTileUrl,
                  userAgentPackageName: 'com.solvitco.vitalfi',
                ),
              PolylineLayer(
                polylines: polylines,
              ),
              MarkerLayer(
                markers: markers,
              ),
            ],
          ),
          Positioned(
            top: 10,
            left: 10,
            child: Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.7),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.white24)
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text("Leyenda Táctica", style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
                  const SizedBox(height: 4),
                  Row(
                    children: const [
                      Icon(Icons.my_location, color: Colors.blue, size: 16),
                      SizedBox(width: 4),
                      Text("Rescatista", style: TextStyle(color: Colors.white70, fontSize: 10))
                    ]
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: const [
                      Icon(Icons.person_pin, color: Colors.greenAccent, size: 16),
                      SizedBox(width: 4),
                      Text("Víctima Estable", style: TextStyle(color: Colors.white70, fontSize: 10))
                    ]
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: const [
                      Icon(Icons.person_pin, color: Colors.red, size: 16),
                      SizedBox(width: 4),
                      Text("Víctima Crítica", style: TextStyle(color: Colors.white70, fontSize: 10))
                    ]
                  ),
                ],
              ),
            ),
          )
        ],
      ),
      floatingActionButton: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton(
            heroTag: 'clearCacheBtn',
            onPressed: () async {
              if (_cacheStore != null) {
                await _cacheStore!.clean(staleOnly: false);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Bóveda de Mapas Offline limpiada.')),
                );
              }
            },
            backgroundColor: Colors.orange,
            child: const Icon(Icons.cleaning_services),
          ),
          const SizedBox(height: 10),
          FloatingActionButton(
            heroTag: 'centerMapBtn',
            onPressed: () {
              _mapController.move(_currentPosition!, 19.0);
            },
            child: const Icon(Icons.center_focus_strong),
          ),
        ],
      ),
    );
  }
}
