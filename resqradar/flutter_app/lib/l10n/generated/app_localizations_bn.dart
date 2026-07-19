// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Bengali Bangla (`bn`).
class AppLocalizationsBn extends AppLocalizations {
  AppLocalizationsBn([String locale = 'bn']) : super(locale);

  @override
  String get appTitle => 'কৌশলগত ResQRadar';

  @override
  String get tabRadar => 'রাডার';

  @override
  String get tabSwarm => 'ঝাঁক';

  @override
  String get tabMap => 'মানচিত্র';

  @override
  String get tabSettings => 'সেটিংস';

  @override
  String get tabHistory => 'ইতিহাস';

  @override
  String get btnStartScan => 'স্ক্যান শুরু করুন';

  @override
  String get btnStopScan => 'থামুন';

  @override
  String get statusSearching => 'অনুসন্ধান করা হচ্ছে...';

  @override
  String get statusDetected => 'ভুক্তভোগী সনাক্ত করা হয়েছে';

  @override
  String get lblBPM => 'BPM';

  @override
  String get lblRPM => 'RPM';

  @override
  String get lblDistance => 'দূরত্ব';

  @override
  String get lblDistanceUnit => 'm';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'ভুক্তভোগী পোর্টালে সংযুক্ত! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'অত্যাবশ্যক আন্দোলন সনাক্ত করা হয়েছে!';

  @override
  String get aboutTitle => 'সম্পর্কিত';

  @override
  String get aboutVersion => 'সংস্করণ 1.0.0+1';

  @override
  String get aboutDeveloper => 'ডেভেলপার:';

  @override
  String get aboutWeb => 'ওয়েব:';

  @override
  String get aboutContact => 'WhatsApp / যোগাযোগ:';

  @override
  String get aboutLicense => 'লাইসেন্স:';

  @override
  String get aboutLicenseDesc => 'Moovitya মালিকানাধীন';

  @override
  String get aboutCredits => 'ক্রেডিট:';

  @override
  String get aboutCreditsDesc => 'কার্লোস মুন্ডারের মূল ধারণার উপর ভিত্তি করে।';

  @override
  String get btnClose => 'বন্ধ করুন';

  @override
  String get swarmTitle => 'P2P সোয়ার্ম মোড';

  @override
  String get swarmSubtitle => 'রেসকিউয়ার টেকটিক্যাল নেটওয়ার্ক';

  @override
  String get swarmStatusConnected => 'মেশের সাথে সংযুক্ত';

  @override
  String get swarmStatusDisconnected => 'বিচ্ছিন্ন';

  @override
  String get btnConnectSwarm => 'মেশ সংযোগ করুন';

  @override
  String get btnDisconnectSwarm => 'সংযোগ বিচ্ছিন্ন করুন';

  @override
  String get chatInputHint => 'কৌশলগত বার্তা...';

  @override
  String get settingsTitle => 'সিস্টেম সেটিংস';

  @override
  String get settingsRadarSensitivity => 'রাডারের সংবেদনশীলতা';

  @override
  String get settingsAlarmSound => 'অ্যালার্ম সাউন্ড';

  @override
  String get settingsVibration => 'কৌশলগত কম্পন';

  @override
  String get mapTitle => 'নিষ্কাশন মানচিত্র';

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
