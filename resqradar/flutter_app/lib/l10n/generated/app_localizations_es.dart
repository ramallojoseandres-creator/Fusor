// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get appTitle => 'ResQRadar';

  @override
  String get tabRadar => 'Radar';

  @override
  String get tabSwarm => 'Enjambre';

  @override
  String get tabMap => 'Mapa';

  @override
  String get tabSettings => 'Ajustes';

  @override
  String get tabHistory => 'Historial';

  @override
  String get btnStartScan => 'INICIAR ESCANEO';

  @override
  String get btnStopScan => 'DETENER';

  @override
  String get statusSearching => 'Buscando...';

  @override
  String get statusDetected => 'Víctima Detectada';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'Distancia';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return '¡VÍCTIMA CONECTADA AL PORTAL! IP: $ip';
  }

  @override
  String get msgMovementDetected => '¡MOVIMIENTO VITAL DETECTADO!';

  @override
  String get aboutTitle => 'Acerca de';

  @override
  String get aboutVersion => 'Versión 1.0.0+1';

  @override
  String get aboutDeveloper => 'Desarrollador:';

  @override
  String get aboutWeb => 'Web:';

  @override
  String get aboutContact => 'WhatsApp / Contacto:';

  @override
  String get aboutLicense => 'Licencia:';

  @override
  String get aboutLicenseDesc =>
      'Uso Profesional y Rescate (Propiedad de Moovitya)';

  @override
  String get aboutCredits => 'Créditos:';

  @override
  String get aboutCreditsDesc =>
      'Basado en el concepto y sistema original creado por Carlos Mundaray.';

  @override
  String get btnClose => 'Cerrar';

  @override
  String get swarmTitle => 'Modo Enjambre P2P';

  @override
  String get swarmSubtitle => 'Red de Rescatistas';

  @override
  String get swarmStatusConnected => 'CONECTADO AL MESH';

  @override
  String get swarmStatusDisconnected => 'DESCONECTADO';

  @override
  String get btnConnectSwarm => 'Conectar a Mesh';

  @override
  String get btnDisconnectSwarm => 'Desconectar';

  @override
  String get chatInputHint => 'Mensaje de rescate...';

  @override
  String get settingsTitle => 'Ajustes del Sistema';

  @override
  String get settingsRadarSensitivity => 'Sensibilidad del Radar';

  @override
  String get settingsAlarmSound => 'Sonido de Alarma';

  @override
  String get settingsVibration => 'Vibración Háptica';

  @override
  String get mapTitle => 'Mapa de Extracción';

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
