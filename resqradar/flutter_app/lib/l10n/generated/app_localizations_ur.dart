// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Urdu (`ur`).
class AppLocalizationsUr extends AppLocalizations {
  AppLocalizationsUr([String locale = 'ur']) : super(locale);

  @override
  String get appTitle => 'ٹیکٹیکل ResQRadar';

  @override
  String get tabRadar => 'ریڈار';

  @override
  String get tabSwarm => 'سوارم';

  @override
  String get tabMap => 'نقشہ';

  @override
  String get tabSettings => 'ترتیبات';

  @override
  String get tabHistory => 'تاریخ';

  @override
  String get btnStartScan => 'سکین شروع کریں';

  @override
  String get btnStopScan => 'رکیں';

  @override
  String get statusSearching => 'تلاش کر رہا ہے...';

  @override
  String get statusDetected => 'متاثرہ شخص کا پتہ چل گیا';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'فاصلہ';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'متاثرہ پورٹل سے جڑ گیا! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'اہم حرکت کا پتہ چلا!';

  @override
  String get aboutTitle => 'کے بارے میں';

  @override
  String get aboutVersion => 'ورژن 1.0.0+1';

  @override
  String get aboutDeveloper => 'ڈویلپر:';

  @override
  String get aboutWeb => 'ویب:';

  @override
  String get aboutContact => 'WhatsApp / رابطہ:';

  @override
  String get aboutLicense => 'لائسنس:';

  @override
  String get aboutLicenseDesc => 'Moovitya کی ملکیت';

  @override
  String get aboutCredits => 'کریڈٹ:';

  @override
  String get aboutCreditsDesc => 'کارلوس منڈارے کے اصل تصور پر مبنی۔';

  @override
  String get btnClose => 'بند کریں';

  @override
  String get swarmTitle => 'P2P سوارم موڈ';

  @override
  String get swarmSubtitle => 'ریسکیو ٹیکٹیکل نیٹ ورک';

  @override
  String get swarmStatusConnected => 'میش سے جڑا ہوا';

  @override
  String get swarmStatusDisconnected => 'منقطع';

  @override
  String get btnConnectSwarm => 'میش سے جڑیں';

  @override
  String get btnDisconnectSwarm => 'منقطع کریں';

  @override
  String get chatInputHint => 'ٹیکٹیکل پیغام...';

  @override
  String get settingsTitle => 'سسٹم کی ترتیبات';

  @override
  String get settingsRadarSensitivity => 'ریڈار کی حساسیت';

  @override
  String get settingsAlarmSound => 'الارم کی آواز';

  @override
  String get settingsVibration => 'ٹیکٹیکل وائبریشن';

  @override
  String get mapTitle => 'انخلاء کا نقشہ';

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
