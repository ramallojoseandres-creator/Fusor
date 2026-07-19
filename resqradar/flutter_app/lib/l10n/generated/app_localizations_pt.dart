// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Portuguese (`pt`).
class AppLocalizationsPt extends AppLocalizations {
  AppLocalizationsPt([String locale = 'pt']) : super(locale);

  @override
  String get appTitle => 'ResQRadar Tático';

  @override
  String get tabRadar => 'Radar';

  @override
  String get tabSwarm => 'Enxame';

  @override
  String get tabMap => 'Mapa';

  @override
  String get tabSettings => 'Configurações';

  @override
  String get tabHistory => 'Histórico';

  @override
  String get btnStartScan => 'INICIAR ESCANEAMENTO';

  @override
  String get btnStopScan => 'PARAR';

  @override
  String get statusSearching => 'Buscando...';

  @override
  String get statusDetected => 'Vítima Detectada';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'Distância';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'VÍTIMA CONECTADA AO PORTAL! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'MOVIMENTO VITAL DETECTADO!';

  @override
  String get aboutTitle => 'Sobre';

  @override
  String get aboutVersion => 'Versão 1.0.0+1';

  @override
  String get aboutDeveloper => 'Desenvolvedor:';

  @override
  String get aboutWeb => 'Site:';

  @override
  String get aboutContact => 'WhatsApp / Contato:';

  @override
  String get aboutLicense => 'Licença:';

  @override
  String get aboutLicenseDesc =>
      'Uso Tático e Resgate (Propriedade da Moovitya)';

  @override
  String get aboutCredits => 'Créditos:';

  @override
  String get aboutCreditsDesc =>
      'Baseado no conceito e sistema original criado por Carlos Mundaray.';

  @override
  String get btnClose => 'Fechar';

  @override
  String get swarmTitle => 'Modo Enxame P2P';

  @override
  String get swarmSubtitle => 'Rede Tática de Resgatistas';

  @override
  String get swarmStatusConnected => 'CONECTADO À MALHA';

  @override
  String get swarmStatusDisconnected => 'DESCONECTADO';

  @override
  String get btnConnectSwarm => 'Conectar à Malha';

  @override
  String get btnDisconnectSwarm => 'Desconectar';

  @override
  String get chatInputHint => 'Mensagem tática...';

  @override
  String get settingsTitle => 'Configurações do Sistema';

  @override
  String get settingsRadarSensitivity => 'Sensibilidade do Radar';

  @override
  String get settingsAlarmSound => 'Som do Alarme';

  @override
  String get settingsVibration => 'Vibração Tática';

  @override
  String get mapTitle => 'Mapa de Extração';

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
