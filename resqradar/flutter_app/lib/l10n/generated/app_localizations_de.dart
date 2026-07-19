// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for German (`de`).
class AppLocalizationsDe extends AppLocalizations {
  AppLocalizationsDe([String locale = 'de']) : super(locale);

  @override
  String get appTitle => 'Taktisches ResQRadar';

  @override
  String get tabRadar => 'Radar';

  @override
  String get tabSwarm => 'Schwarm';

  @override
  String get tabMap => 'Karte';

  @override
  String get tabSettings => 'Einstellungen';

  @override
  String get tabHistory => 'Verlauf';

  @override
  String get btnStartScan => 'SCAN STARTEN';

  @override
  String get btnStopScan => 'STOPP';

  @override
  String get statusSearching => 'Suchen...';

  @override
  String get statusDetected => 'Opfer Erkannt';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'Entfernung';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'OPFER VERBUNDEN! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'LEBENSZEICHEN ERKANNT!';

  @override
  String get aboutTitle => 'Über';

  @override
  String get aboutVersion => 'Version 1.0.0+1';

  @override
  String get aboutDeveloper => 'Entwickler:';

  @override
  String get aboutWeb => 'Web:';

  @override
  String get aboutContact => 'WhatsApp / Kontakt:';

  @override
  String get aboutLicense => 'Lizenz:';

  @override
  String get aboutLicenseDesc => 'Taktische und Rettungsnutzung (Moovitya)';

  @override
  String get aboutCredits => 'Credits:';

  @override
  String get aboutCreditsDesc =>
      'Basierend auf dem System von Carlos Mundaray.';

  @override
  String get btnClose => 'Schließen';

  @override
  String get swarmTitle => 'P2P Schwarm-Modus';

  @override
  String get swarmSubtitle => 'Taktisches Rettungsnetzwerk';

  @override
  String get swarmStatusConnected => 'MIT MESH VERBUNDEN';

  @override
  String get swarmStatusDisconnected => 'GETRENNT';

  @override
  String get btnConnectSwarm => 'Mit Mesh verbinden';

  @override
  String get btnDisconnectSwarm => 'Trennen';

  @override
  String get chatInputHint => 'Taktische Nachricht...';

  @override
  String get settingsTitle => 'Systemeinstellungen';

  @override
  String get settingsRadarSensitivity => 'Radarempfindlichkeit';

  @override
  String get settingsAlarmSound => 'Alarmton';

  @override
  String get settingsVibration => 'Taktische Vibration';

  @override
  String get mapTitle => 'Evakuierungskarte';

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
