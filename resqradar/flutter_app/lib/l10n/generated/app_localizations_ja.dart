// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Japanese (`ja`).
class AppLocalizationsJa extends AppLocalizations {
  AppLocalizationsJa([String locale = 'ja']) : super(locale);

  @override
  String get appTitle => '戦術的 ResQRadar';

  @override
  String get tabRadar => 'レーダー';

  @override
  String get tabSwarm => 'スウォーム';

  @override
  String get tabMap => 'マップ';

  @override
  String get tabSettings => '設定';

  @override
  String get tabHistory => '履歴';

  @override
  String get btnStartScan => 'スキャン開始';

  @override
  String get btnStopScan => '停止';

  @override
  String get statusSearching => '検索中...';

  @override
  String get statusDetected => '生存者検知';

  @override
  String get lblBPM => '心拍数';

  @override
  String get lblRPM => '呼吸数';

  @override
  String get lblDistance => '距離';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return '生存者がポータルに接続しました！ IP: $ip';
  }

  @override
  String get msgMovementDetected => '生体反応を検知！';

  @override
  String get aboutTitle => 'アプリについて';

  @override
  String get aboutVersion => 'バージョン 1.0.0+1';

  @override
  String get aboutDeveloper => '開発者:';

  @override
  String get aboutWeb => 'Web:';

  @override
  String get aboutContact => 'WhatsApp / 連絡先:';

  @override
  String get aboutLicense => 'ライセンス:';

  @override
  String get aboutLicenseDesc => '戦術および救助用 (Moovitya 所有)';

  @override
  String get aboutCredits => 'クレジット:';

  @override
  String get aboutCreditsDesc => 'Carlos Mundaray によるオリジナルコンセプト。';

  @override
  String get btnClose => '閉じる';

  @override
  String get swarmTitle => 'P2P スウォームモード';

  @override
  String get swarmSubtitle => '救助隊戦術ネットワーク';

  @override
  String get swarmStatusConnected => 'メッシュに接続済み';

  @override
  String get swarmStatusDisconnected => '切断';

  @override
  String get btnConnectSwarm => 'メッシュに接続';

  @override
  String get btnDisconnectSwarm => '切断する';

  @override
  String get chatInputHint => '戦術メッセージ...';

  @override
  String get settingsTitle => 'システム設定';

  @override
  String get settingsRadarSensitivity => 'レーダー感度';

  @override
  String get settingsAlarmSound => 'アラーム音';

  @override
  String get settingsVibration => '戦術的振動';

  @override
  String get mapTitle => '救出マップ';

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
