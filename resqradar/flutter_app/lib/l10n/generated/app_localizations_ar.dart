// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Arabic (`ar`).
class AppLocalizationsAr extends AppLocalizations {
  AppLocalizationsAr([String locale = 'ar']) : super(locale);

  @override
  String get appTitle => 'رادار الإنقاذ التكتيكي';

  @override
  String get tabRadar => 'الرادار';

  @override
  String get tabSwarm => 'السرب';

  @override
  String get tabMap => 'الخريطة';

  @override
  String get tabSettings => 'الإعدادات';

  @override
  String get tabHistory => 'السجل';

  @override
  String get btnStartScan => 'بدء المسح';

  @override
  String get btnStopScan => 'إيقاف';

  @override
  String get statusSearching => 'جاري البحث...';

  @override
  String get statusDetected => 'تم اكتشاف ضحية';

  @override
  String get lblBPM => 'نبضة/د';

  @override
  String get lblRPM => 'تنفس/د';

  @override
  String get lblDistance => 'المسافة';

  @override
  String get lblDistanceUnit => 'م';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'تم اتصال الضحية بالبوابة! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'تم اكتشاف حركة حيوية!';

  @override
  String get aboutTitle => 'حول';

  @override
  String get aboutVersion => 'الإصدار 1.0.0+1';

  @override
  String get aboutDeveloper => 'المطور:';

  @override
  String get aboutWeb => 'الويب:';

  @override
  String get aboutContact => 'واتساب / اتصال:';

  @override
  String get aboutLicense => 'الترخيص:';

  @override
  String get aboutLicenseDesc => 'استخدام تكتيكي وإنقاذ (ملكية Moovitya)';

  @override
  String get aboutCredits => 'الاعتمادات:';

  @override
  String get aboutCreditsDesc =>
      'استنادًا إلى المفهوم الأصلي لـ Carlos Mundaray.';

  @override
  String get btnClose => 'إغلاق';

  @override
  String get swarmTitle => 'وضع السرب P2P';

  @override
  String get swarmSubtitle => 'الشبكة التكتيكية للمنقذين';

  @override
  String get swarmStatusConnected => 'متصل بالشبكة المعشقة';

  @override
  String get swarmStatusDisconnected => 'غير متصل';

  @override
  String get btnConnectSwarm => 'اتصال بالشبكة';

  @override
  String get btnDisconnectSwarm => 'قطع الاتصال';

  @override
  String get chatInputHint => 'رسالة تكتيكية...';

  @override
  String get settingsTitle => 'إعدادات النظام';

  @override
  String get settingsRadarSensitivity => 'حساسية الرادار';

  @override
  String get settingsAlarmSound => 'صوت التنبيه';

  @override
  String get settingsVibration => 'اهتزاز تكتيكي';

  @override
  String get mapTitle => 'خريطة الإخلاء';

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
