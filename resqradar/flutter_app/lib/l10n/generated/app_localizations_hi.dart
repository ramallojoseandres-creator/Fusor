// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Hindi (`hi`).
class AppLocalizationsHi extends AppLocalizations {
  AppLocalizationsHi([String locale = 'hi']) : super(locale);

  @override
  String get appTitle => 'सामरिक ResQRadar';

  @override
  String get tabRadar => 'रडार';

  @override
  String get tabSwarm => 'स्वार्म';

  @override
  String get tabMap => 'नक्शा';

  @override
  String get tabSettings => 'सेटिंग्स';

  @override
  String get tabHistory => 'इतिहास';

  @override
  String get btnStartScan => 'स्कैन शुरू करें';

  @override
  String get btnStopScan => 'रोकें';

  @override
  String get statusSearching => 'खोज रहा है...';

  @override
  String get statusDetected => 'पीड़ित का पता चला';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'दूरी';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'पीड़ित पोर्टल से जुड़ा! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'महत्वपूर्ण गति का पता चला!';

  @override
  String get aboutTitle => 'के बारे में';

  @override
  String get aboutVersion => 'संस्करण 1.0.0+1';

  @override
  String get aboutDeveloper => 'डेवलपर:';

  @override
  String get aboutWeb => 'वेबसाइट:';

  @override
  String get aboutContact => 'WhatsApp / संपर्क:';

  @override
  String get aboutLicense => 'लाइसेंस:';

  @override
  String get aboutLicenseDesc => 'सामरिक और बचाव उपयोग (Moovitya)';

  @override
  String get aboutCredits => 'क्रेडिट:';

  @override
  String get aboutCreditsDesc => 'Carlos Mundaray की मूल अवधारणा पर आधारित।';

  @override
  String get btnClose => 'बंद करें';

  @override
  String get swarmTitle => 'P2P स्वार्म मोड';

  @override
  String get swarmSubtitle => 'बचावकर्ता सामरिक नेटवर्क';

  @override
  String get swarmStatusConnected => 'मेश से जुड़ा हुआ';

  @override
  String get swarmStatusDisconnected => 'डिस्कनेक्ट हो गया';

  @override
  String get btnConnectSwarm => 'मेश से जुड़ें';

  @override
  String get btnDisconnectSwarm => 'डिस्कनेक्ट करें';

  @override
  String get chatInputHint => 'सामरिक संदेश...';

  @override
  String get settingsTitle => 'सिस्टम सेटिंग्स';

  @override
  String get settingsRadarSensitivity => 'रडार संवेदनशीलता';

  @override
  String get settingsAlarmSound => 'अलार्म ध्वनि';

  @override
  String get settingsVibration => 'सामरिक कंपन';

  @override
  String get mapTitle => 'निकासी का नक्शा';

  @override
  String victimModalTitle(String id) {
    return '$id - Viva - respira y pulso detectado';
  }

  @override
  String get victimModalLocalData => 'Datos locales Wi-Fi';

  @override
  String get victimModalCoordX => 'Coordenadas X';

  @override
  String get victimModalCoordY => 'Coordenadas Y';

  @override
  String get victimModalDepth => 'Profundidad';

  @override
  String get victimModalDepthValue => '0,30 m bajo escombros';

  @override
  String get victimModalHeading => 'Rumbo';

  @override
  String get victimModalProximity => 'Proximidad';

  @override
  String get victimModalProxClose => 'MUY CERCA';

  @override
  String get victimModalProxFar => 'LEJOS';

  @override
  String get victimModalBreathing => 'Respirando';

  @override
  String victimModalBreathingYes(String rpm) {
    return 'Sí - $rpm rpm';
  }

  @override
  String get victimModalHeart => 'Corazón latiendo';

  @override
  String victimModalHeartYes(String bpm) {
    return 'Sí - $bpm lpm';
  }

  @override
  String get victimModalActivity => 'Actividad';

  @override
  String get victimModalActivityYes => 'Sí';

  @override
  String get victimModalConfVital => 'Confianza vital';

  @override
  String get victimModalConfDebris => 'Confianza escombros';

  @override
  String get msgStopScanCalibrate => 'Detén el escaneo para calibrar.';

  @override
  String msgNoiseFloor(String noise) {
    return 'Noise Floor: $noise dBm';
  }

  @override
  String get titleConnectedDevices => 'Dispositivos Conectados';

  @override
  String get historyTitle => 'Historial de Búsquedas';

  @override
  String get historyEmpty => 'No hay rescates registrados.';

  @override
  String historyRescue(String id) {
    return 'Rescate #$id';
  }

  @override
  String historyDate(String date) {
    return 'Fecha: $date';
  }

  @override
  String historyDuration(String sec) {
    return 'Duración: ${sec}s';
  }

  @override
  String historyDetections(String count) {
    return 'Detecciones: $count';
  }

  @override
  String historyDetailTitle(String id) {
    return 'Detalle de Rescate #$id';
  }

  @override
  String get historyDetailVictims => 'Detalle de Víctimas:';

  @override
  String historyVictim(String id) {
    return 'Víctima: $id';
  }

  @override
  String historyHeart(String bpm) {
    return 'Ritmo Cardíaco: $bpm BPM';
  }

  @override
  String historyBreath(String rpm) {
    return 'Respiración: $rpm RPM';
  }

  @override
  String historyError(String err) {
    return 'Error al leer datos: $err';
  }

  @override
  String get settingsCancel => 'Cancelar';

  @override
  String get settingsSave => 'Guardar';

  @override
  String get settingsMinFreq => 'Frecuencia Mínima de Respiración';

  @override
  String get settingsMaxFreq => 'Frecuencia Máxima de Respiración';

  @override
  String get settingsMotionCancel => 'Cancelación de Movimiento';

  @override
  String get settingsMotionCancelDesc =>
      'Usa el acelerómetro para reducir ruido';

  @override
  String get settingsNightVision => 'Modo Visión Nocturna';

  @override
  String get settingsNightVisionDesc =>
      'Alto contraste para operaciones con poca luz';

  @override
  String get settingsBgScan => 'Escaneo en Segundo Plano';

  @override
  String get settingsBgScanDesc => 'Continúa escaneando al apagar la pantalla';

  @override
  String get swarmRestart => 'Reiniciar Búsqueda P2P';

  @override
  String swarmStatus(String status) {
    return 'Estado: $status';
  }
}
