// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => '战术救援雷达 (ResQRadar)';

  @override
  String get tabRadar => '雷达';

  @override
  String get tabSwarm => '蜂群';

  @override
  String get tabMap => '地图';

  @override
  String get tabSettings => '设置';

  @override
  String get tabHistory => '历史记录';

  @override
  String get btnStartScan => '开始扫描';

  @override
  String get btnStopScan => '停止';

  @override
  String get statusSearching => '搜索中...';

  @override
  String get statusDetected => '检测到受害者';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => '距离';

  @override
  String get lblDistanceUnit => '米';

  @override
  String msgVictimPortalConnected(String ip) {
    return '受害者已连接到门户！IP: $ip';
  }

  @override
  String get msgMovementDetected => '检测到生命体征！';

  @override
  String get aboutTitle => '关于';

  @override
  String get aboutVersion => '版本 1.0.0+1';

  @override
  String get aboutDeveloper => '开发者:';

  @override
  String get aboutWeb => '网站:';

  @override
  String get aboutContact => 'WhatsApp / 联系方式:';

  @override
  String get aboutLicense => '许可证:';

  @override
  String get aboutLicenseDesc => '战术和救援用途 (Moovitya财产)';

  @override
  String get aboutCredits => '鸣谢:';

  @override
  String get aboutCreditsDesc => '基于 Carlos Mundaray 的原创概念和系统。';

  @override
  String get btnClose => '关闭';

  @override
  String get swarmTitle => 'P2P 蜂群模式';

  @override
  String get swarmSubtitle => '救援人员战术网络';

  @override
  String get swarmStatusConnected => '已连接到网状网络';

  @override
  String get swarmStatusDisconnected => '已断开连接';

  @override
  String get btnConnectSwarm => '连接到网状网络';

  @override
  String get btnDisconnectSwarm => '断开连接';

  @override
  String get chatInputHint => '战术消息...';

  @override
  String get settingsTitle => '系统设置';

  @override
  String get settingsRadarSensitivity => '雷达灵敏度';

  @override
  String get settingsAlarmSound => '警报声';

  @override
  String get settingsVibration => '战术震动';

  @override
  String get mapTitle => '撤离地图';

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
