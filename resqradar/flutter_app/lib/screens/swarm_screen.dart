import 'package:vitalfiapps/l10n/generated/app_localizations.dart';
import 'package:flutter/material.dart';

import 'package:permission_handler/permission_handler.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';
import '../services/swarm_service.dart';
import '../config/app_config.dart';
import '../l10n/generated/app_localizations.dart';

class SwarmScreen extends StatefulWidget {
  const SwarmScreen({super.key});

  @override
  State<SwarmScreen> createState() => _SwarmScreenState();
}

class _SwarmScreenState extends State<SwarmScreen> with TickerProviderStateMixin {
  late final SwarmService _swarmService;
  late final AnimationController _animationController;
  Position? _currentPosition;
  late final TabController _tabController;
  final TextEditingController _chatController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _swarmService = SwarmService();
    
    _requestPermissions().then((_) {
      _swarmService.initSwarm();
    });

    _tabController = TabController(length: 3, vsync: this);

    _animationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat(reverse: true);
    
    _getCurrentLocation();
  }

  Future<void> _getCurrentLocation() async {
    try {
      Position position = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
      if (mounted) {
        setState(() {
          _currentPosition = position;
        });
      }
    } catch (e) {
      print("GPS not available");
    }
  }

  Future<void> _requestPermissions() async {
    await [
      Permission.location,
      Permission.bluetooth,
      Permission.bluetoothScan,
      Permission.bluetoothAdvertise,
      Permission.bluetoothConnect,
      Permission.nearbyWifiDevices,
    ].request();
  }

  @override
  void dispose() {
    _animationController.dispose();
    _tabController.dispose();
    _chatController.dispose();
    // No detenemos el Enjambre al cerrar la pantalla para que siga operando en segundo plano
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.swarmTitle),
        backgroundColor: Colors.transparent,
        elevation: 0,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(icon: Icon(Icons.list), text: "Lista Nodos"),
            Tab(icon: Icon(Icons.map), text: "Mapa Táctico"),
            Tab(icon: Icon(Icons.chat), text: "Chat Global"),
          ],
        ),
      ),
      body: AnimatedBuilder(
        animation: _swarmService,
        builder: (context, child) {
          return TabBarView(
            controller: _tabController,
            physics: const NeverScrollableScrollPhysics(), // Evita problemas con el gesto del mapa
            children: [
              // PESTAÑA 1: LISTA ORIGINAL
              SafeArea(
                child: Column(
                  children: [
                    const SizedBox(height: 30),
                    Center(
                      child: AnimatedBuilder(
                        animation: _animationController,
                        builder: (context, child) {
                          return Container(
                            width: 150 + (_animationController.value * 30),
                            height: 150 + (_animationController.value * 30),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Theme.of(context).primaryColor.withOpacity(0.1 + (_animationController.value * 0.2)),
                              border: Border.all(color: Theme.of(context).primaryColor, width: 2),
                            ),
                            child: Icon(
                              Icons.wifi_tethering,
                              size: 60,
                              color: Theme.of(context).primaryColor,
                            ),
                          );
                        },
                      ),
                    ),
                    const SizedBox(height: 30),
                    Text(
                      _swarmService.isSearching 
                          ? 'Buscando rescatistas en el área...' 
                          : 'Modo Enjambre inactivo',
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 20),
                    Expanded(
                      child: _swarmService.connectedNodes.isEmpty
                          ? const Center(
                              child: Text(
                                'No hay nadie conectado cerca.\n(Abre la app en otro teléfono)',
                                textAlign: TextAlign.center,
                                style: TextStyle(color: Colors.grey),
                              ),
                            )
                          : ListView.builder(
                              itemCount: _swarmService.connectedNodes.length,
                              itemBuilder: (context, index) {
                                var node = _swarmService.connectedNodes[index];
                                return Card(
                                  margin: const EdgeInsets.symmetric(horizontal: 20, vertical: 10),
                                  elevation: 4,
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                                  child: ListTile(
                                    leading: const CircleAvatar(
                                      backgroundColor: Colors.green,
                                      child: Icon(Icons.person, color: Colors.white),
                                    ),
                                    title: Text(node.deviceName),
                                    subtitle: Text(AppLocalizations.of(context)!.swarmStatus(node.status.toString())),
                                    trailing: Column(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        const Icon(Icons.pin_drop, color: Colors.blue, size: 20),
                                        Text(
                                          '${node.distance} m',
                                          style: const TextStyle(fontWeight: FontWeight.bold),
                                        ),
                                      ],
                                    ),
                                  ),
                                );
                              },
                            ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(20.0),
                      child: ElevatedButton.icon(
                        onPressed: () async {
                          await _swarmService.stopSwarm();
                          _swarmService.initSwarm();
                        },
                        icon: const Icon(Icons.refresh),
                        label: Text(AppLocalizations.of(context)!.swarmRestart),
                        style: ElevatedButton.styleFrom(
                          minimumSize: const Size(double.infinity, 50),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(15)),
                        ),
                      ),
                    )
                  ],
                ),
              ),
              // PESTAÑA 2: MAPA TÁCTICO
              _currentPosition == null
                  ? const Center(child: CircularProgressIndicator(color: Colors.red))
                  : FlutterMap(
                      options: MapOptions(
                        initialCenter: LatLng(_currentPosition!.latitude, _currentPosition!.longitude),
                        initialZoom: 17.0,
                      ),
                      children: [
                        TileLayer(
                          urlTemplate: AppConfig.mapTileUrl,
                          userAgentPackageName: 'com.solvitco.vitalfi',
                        ),
                        MarkerLayer(
                          markers: [
                            // Mi ubicación (Punto Azul)
                            Marker(
                              point: LatLng(_currentPosition!.latitude, _currentPosition!.longitude),
                              width: 80,
                              height: 80,
                              child: const Icon(Icons.my_location, color: Colors.blue, size: 40),
                            ),
                            // Nodos conectados (Puntos Rojos)
                            ..._swarmService.connectedNodes.where((n) => n.latitude != 0.0 && n.longitude != 0.0).map(
                              (node) => Marker(
                                point: LatLng(node.latitude, node.longitude),
                                width: 80,
                                height: 80,
                                child: Column(
                                  children: [
                                    const Icon(Icons.location_on, color: Colors.red, size: 40),
                                    Container(
                                      color: Colors.white.withOpacity(0.7),
                                      child: Text(node.deviceName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 10)),
                                    )
                                  ],
                                ),
                              )
                            )
                          ],
                        )
                      ],
                    ),
              // PESTAÑA 3: CHAT GLOBAL
              SafeArea(
                child: Column(
                  children: [
                    Expanded(
                      child: ListView.builder(
                        itemCount: _swarmService.chatMessages.length,
                        itemBuilder: (context, index) {
                          var msg = _swarmService.chatMessages[index];
                          bool isMe = msg.sender == "Yo";
                          return Align(
                            alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: isMe ? Colors.green.shade800 : Colors.grey.shade800,
                                borderRadius: BorderRadius.circular(15),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    msg.sender,
                                    style: TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 12,
                                      color: isMe ? Colors.white70 : Colors.blueAccent,
                                    ),
                                  ),
                                  const SizedBox(height: 5),
                                  Text(msg.text, style: const TextStyle(fontSize: 16)),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _chatController,
                              decoration: InputDecoration(
                                hintText: "Mensaje a todo el escuadrón...",
                                filled: true,
                                fillColor: Colors.grey.shade900,
                                border: OutlineInputBorder(
                                  borderRadius: BorderRadius.circular(20),
                                  borderSide: BorderSide.none,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          CircleAvatar(
                            backgroundColor: Colors.green,
                            child: IconButton(
                              icon: const Icon(Icons.send, color: Colors.white),
                              onPressed: () {
                                if (_chatController.text.isNotEmpty) {
                                  _swarmService.sendChatMessage(_chatController.text);
                                  _chatController.clear();
                                }
                              },
                            ),
                          )
                        ],
                      ),
                    )
                  ],
                ),
              )
            ],
          );
        },
      ),
    );
  }
}
