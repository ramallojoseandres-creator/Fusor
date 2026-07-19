// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppLocalizationsFr extends AppLocalizations {
  AppLocalizationsFr([String locale = 'fr']) : super(locale);

  @override
  String get appTitle => 'ResQRadar Tactique';

  @override
  String get tabRadar => 'Radar';

  @override
  String get tabSwarm => 'Essaim';

  @override
  String get tabMap => 'Carte';

  @override
  String get tabSettings => 'Paramètres';

  @override
  String get tabHistory => 'Historique';

  @override
  String get btnStartScan => 'DÉMARRER';

  @override
  String get btnStopScan => 'ARRÊTER';

  @override
  String get statusSearching => 'Recherche...';

  @override
  String get statusDetected => 'Victime Détectée';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'Distance';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'VICTIME CONNECTÉE AU PORTAIL! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'MOUVEMENT VITAL DÉTECTÉ!';

  @override
  String get aboutTitle => 'À propos';

  @override
  String get aboutVersion => 'Version 1.0.0+1';

  @override
  String get aboutDeveloper => 'Développeur:';

  @override
  String get aboutWeb => 'Web:';

  @override
  String get aboutContact => 'WhatsApp / Contact:';

  @override
  String get aboutLicense => 'Licence:';

  @override
  String get aboutLicenseDesc =>
      'Usage Tactique et Sauvetage (Propriété de Moovitya)';

  @override
  String get aboutCredits => 'Crédits:';

  @override
  String get aboutCreditsDesc =>
      'Basé sur le concept et le système original de Carlos Mundaray.';

  @override
  String get btnClose => 'Fermer';

  @override
  String get swarmTitle => 'Mode Essaim P2P';

  @override
  String get swarmSubtitle => 'Réseau Tactique de Sauvetage';

  @override
  String get swarmStatusConnected => 'CONNECTÉ AU MESH';

  @override
  String get swarmStatusDisconnected => 'DÉCONNECTÉ';

  @override
  String get btnConnectSwarm => 'Connecter au Mesh';

  @override
  String get btnDisconnectSwarm => 'Déconnecter';

  @override
  String get chatInputHint => 'Message tactique...';

  @override
  String get settingsTitle => 'Paramètres du Système';

  @override
  String get settingsRadarSensitivity => 'Sensibilité du Radar';

  @override
  String get settingsAlarmSound => 'Son de l\'Alarme';

  @override
  String get settingsVibration => 'Vibration Tactique';

  @override
  String get mapTitle => 'Carte d\'Extraction';

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
