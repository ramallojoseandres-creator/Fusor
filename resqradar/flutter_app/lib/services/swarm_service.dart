import 'dart:math';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:nearby_connections/nearby_connections.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';

class ChatMessage {
  final String sender;
  final String text;
  final DateTime time;
  ChatMessage({required this.sender, required this.text, required this.time});

  Map<String, dynamic> toJson() => {
    'sender': sender,
    'text': text,
    'time': time.toIso8601String(),
  };

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      sender: json['sender'],
      text: json['text'],
      time: DateTime.parse(json['time']),
    );
  }
}

class SwarmNode {
  final String endpointId;
  final String deviceName;
  String status;
  double distance;
  double latitude;
  double longitude;

  SwarmNode({
    required this.endpointId,
    required this.deviceName,
    this.status = "Conectando...",
    this.distance = 0.0,
    this.latitude = 0.0,
    this.longitude = 0.0,
  });
}

class SwarmService extends ChangeNotifier {
  static final SwarmService _instance = SwarmService._internal();
  factory SwarmService() => _instance;
  SwarmService._internal();

  final Strategy strategy = Strategy.P2P_CLUSTER;
  String userName = "Rescatista_..."; // Se actualizará en initSwarm
  bool _isNameLoaded = false;
  
  List<SwarmNode> connectedNodes = [];
  List<ChatMessage> chatMessages = [];
  bool isSearching = false;

  Future<void> _loadOrGenerateName() async {
    if (_isNameLoaded) return;
    final prefs = await SharedPreferences.getInstance();
    String? savedName = prefs.getString('swarm_username');
    if (savedName == null) {
      savedName = "Rescatista_${Random().nextInt(1000)}";
      await prefs.setString('swarm_username', savedName);
    }
    userName = savedName;
    _isNameLoaded = true;
    _loadChatHistory();
  }

  Future<void> _loadChatHistory() async {
    final prefs = await SharedPreferences.getInstance();
    List<String>? savedChats = prefs.getStringList('swarm_chats');
    if (savedChats != null) {
      chatMessages = savedChats.map((msgStr) => ChatMessage.fromJson(jsonDecode(msgStr))).toList();
      notifyListeners();
    }
  }

  Future<void> _saveChatHistory() async {
    final prefs = await SharedPreferences.getInstance();
    List<String> savedChats = chatMessages.map((msg) => jsonEncode(msg.toJson())).toList();
    await prefs.setStringList('swarm_chats', savedChats);
  }

  Future<void> initSwarm() async {
    if (isSearching) return; // Ya está inicializado
    isSearching = true;
    notifyListeners();
    
    await _loadOrGenerateName();
    
    try {
      await Nearby().stopAdvertising();
      await Nearby().stopDiscovery();
    } catch (_) {}

    try {
      await Nearby().startAdvertising(
        userName,
        strategy,
        onConnectionInitiated: _onConnectionInit,
        onConnectionResult: (id, status) {
          _updateNodeStatus(id, status == Status.CONNECTED ? "Conectado" : "Desconectado");
        },
        onDisconnected: (id) {
          _removeNode(id);
        },
      );

      await Nearby().startDiscovery(
        userName,
        strategy,
        onEndpointFound: (id, name, serviceId) {
          // Si encontramos a alguien, pedimos conectarnos automáticamente
          Nearby().requestConnection(
            userName,
            id,
            onConnectionInitiated: _onConnectionInit,
            onConnectionResult: (id, status) {
              _updateNodeStatus(id, status == Status.CONNECTED ? "Conectado" : "Desconectado");
            },
            onDisconnected: (id) {
              _removeNode(id);
            },
          );
        },
        onEndpointLost: (id) {
          if (id != null) {
            _removeNode(id);
          }
        },
      );
    } catch (e) {
      debugPrint("Error iniciando Enjambre: \$e");
    }
  }

  void _onConnectionInit(String id, ConnectionInfo info) {
    _addOrUpdateNode(id, info.endpointName, "Conectando...");
    Nearby().acceptConnection(
      id,
      onPayLoadRecieved: (endpointId, payload) {
        if (payload.type == PayloadType.BYTES && payload.bytes != null) {
          String data = String.fromCharCodes(payload.bytes!);
          _handleReceivedData(endpointId, data);
        }
      },
      onPayloadTransferUpdate: (endpointId, payloadTransferUpdate) {},
    );
  }

  void _handleReceivedData(String senderId, String payload) {
    if (payload.startsWith('RSSI:')) {
      List<String> parts = payload.split('|');
      double rssi = -100.0;
      double lat = 0.0;
      double lng = 0.0;
      
      for (var part in parts) {
        if (part.startsWith('RSSI:')) {
          rssi = double.tryParse(part.split(':')[1]) ?? -100.0;
        } else if (part.startsWith('LAT:')) {
          lat = double.tryParse(part.split(':')[1]) ?? 0.0;
        } else if (part.startsWith('LNG:')) {
          lng = double.tryParse(part.split(':')[1]) ?? 0.0;
        } else if (part.startsWith('MSG:')) {
          String msgText = part.substring(4);
          String senderName = connectedNodes.firstWhere((n) => n.endpointId == senderId, orElse: () => SwarmNode(endpointId: senderId, deviceName: "Desconocido")).deviceName;
          chatMessages.add(ChatMessage(sender: senderName, text: msgText, time: DateTime.now()));
          _saveChatHistory();
          notifyListeners();
        }
      }
      
      double estimatedDistance = _calculateDistance(rssi);

      int index = connectedNodes.indexWhere((node) => node.endpointId == senderId);
      if (index != -1) {
        connectedNodes[index].distance = estimatedDistance;
        if (lat != 0.0 && lng != 0.0) {
          connectedNodes[index].latitude = lat;
          connectedNodes[index].longitude = lng;
        }
        notifyListeners();
      }
    } else if (payload.startsWith('MSG:')) {
      String msgText = payload.substring(4);
      String senderName = connectedNodes.firstWhere((n) => n.endpointId == senderId, orElse: () => SwarmNode(endpointId: senderId, deviceName: "Desconocido")).deviceName;
      chatMessages.add(ChatMessage(sender: senderName, text: msgText, time: DateTime.now()));
      _saveChatHistory();
      notifyListeners();
    }
  }

  void _addOrUpdateNode(String id, String name, String status) {
    int index = connectedNodes.indexWhere((node) => node.endpointId == id);
    if (index == -1) {
      connectedNodes.add(SwarmNode(endpointId: id, deviceName: name, status: status));
    } else {
      connectedNodes[index].status = status;
    }
    notifyListeners();
  }

  void _updateNodeStatus(String id, String status) {
    int index = connectedNodes.indexWhere((node) => node.endpointId == id);
    if (index != -1) {
      connectedNodes[index].status = status;
      notifyListeners();
    }
  }

  void _removeNode(String id) {
    connectedNodes.removeWhere((node) => node.endpointId == id);
    notifyListeners();
  }

  Future<Position?> _getCurrentLocation() async {
    try {
      bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) return null;

      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
        if (permission == LocationPermission.denied) return null;
      }

      if (permission == LocationPermission.deniedForever) return null;

      return await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
    } catch (e) {
      return null;
    }
  }

  void broadcastRSSI(double rssiValue) async {
    if (connectedNodes.isNotEmpty) {
      Position? position = await _getCurrentLocation();
      String payloadStr = 'RSSI:$rssiValue';
      
      if (position != null) {
        payloadStr += '|LAT:${position.latitude}|LNG:${position.longitude}';
      }

      Uint8List bytes = Uint8List.fromList(payloadStr.codeUnits);
      _sendToAllConnected(bytes);
    }
  }

  void sendChatMessage(String text) {
    chatMessages.add(ChatMessage(sender: "Yo", text: text, time: DateTime.now()));
    _saveChatHistory();
    notifyListeners();

    if (connectedNodes.isNotEmpty) {
      String payloadStr = 'MSG:$text';
      Uint8List bytes = Uint8List.fromList(payloadStr.codeUnits);
      _sendToAllConnected(bytes);
    }
  }

  void _sendToAllConnected(Uint8List bytes) {
    for (var node in connectedNodes) {
      if (node.status == "Conectado") {
        Nearby().sendBytesPayload(node.endpointId, bytes);
      }
    }
  }

  Future<void> stopSwarm() async {
    isSearching = false;
    await Nearby().stopAdvertising();
    await Nearby().stopDiscovery();
    await Nearby().stopAllEndpoints();
    connectedNodes.clear();
    notifyListeners();
  }

  double _calculateDistance(double rssi) {
    const double measuredPower = -59.0;
    const double environmentalFactor = 3.0;
    if (rssi == 0) return -1.0;
    
    double distance = pow(10.0, (measuredPower - rssi) / (10 * environmentalFactor)).toDouble();
    return double.parse(distance.toStringAsFixed(2));
  }
}
