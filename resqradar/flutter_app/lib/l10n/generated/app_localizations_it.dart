// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Italian (`it`).
class AppLocalizationsIt extends AppLocalizations {
  AppLocalizationsIt([String locale = 'it']) : super(locale);

  @override
  String get appTitle => 'ResQRadar Tattico';

  @override
  String get tabRadar => 'Radar';

  @override
  String get tabSwarm => 'Sciame';

  @override
  String get tabMap => 'Mappa';

  @override
  String get tabSettings => 'Impostazioni';

  @override
  String get tabHistory => 'Cronologia';

  @override
  String get btnStartScan => 'INIZIA SCANSIONE';

  @override
  String get btnStopScan => 'FERMA';

  @override
  String get statusSearching => 'Ricerca in corso...';

  @override
  String get statusDetected => 'Vittima Rilevata';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'Distanza';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'VITTIMA CONNESSA AL PORTALE! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'MOVIMENTO VITALE RILEVATO!';

  @override
  String get aboutTitle => 'Informazioni';

  @override
  String get aboutVersion => 'Versione 1.0.0+1';

  @override
  String get aboutDeveloper => 'Sviluppatore:';

  @override
  String get aboutWeb => 'Web:';

  @override
  String get aboutContact => 'WhatsApp / Contatto:';

  @override
  String get aboutLicense => 'Licenza:';

  @override
  String get aboutLicenseDesc => 'Uso Tattico e Salvataggio (Moovitya)';

  @override
  String get aboutCredits => 'Crediti:';

  @override
  String get aboutCreditsDesc =>
      'Basato sul sistema originale di Carlos Mundaray.';

  @override
  String get btnClose => 'Chiudi';

  @override
  String get swarmTitle => 'Modalità Sciame P2P';

  @override
  String get swarmSubtitle => 'Rete Tattica Soccorritori';

  @override
  String get swarmStatusConnected => 'CONNESSO AL MESH';

  @override
  String get swarmStatusDisconnected => 'DISCONNESSO';

  @override
  String get btnConnectSwarm => 'Connetti al Mesh';

  @override
  String get btnDisconnectSwarm => 'Disconnetti';

  @override
  String get chatInputHint => 'Messaggio tattico...';

  @override
  String get settingsTitle => 'Impostazioni di Sistema';

  @override
  String get settingsRadarSensitivity => 'Sensibilità Radar';

  @override
  String get settingsAlarmSound => 'Suono Allarme';

  @override
  String get settingsVibration => 'Vibrazione Tattica';

  @override
  String get mapTitle => 'Mappa di Estrazione';

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
