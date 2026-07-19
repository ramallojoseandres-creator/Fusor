// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get appTitle => 'Тактический ResQRadar';

  @override
  String get tabRadar => 'Радар';

  @override
  String get tabSwarm => 'Рой';

  @override
  String get tabMap => 'Карта';

  @override
  String get tabSettings => 'Настройки';

  @override
  String get tabHistory => 'История';

  @override
  String get btnStartScan => 'НАЧАТЬ СКАН.';

  @override
  String get btnStopScan => 'СТОП';

  @override
  String get statusSearching => 'Поиск...';

  @override
  String get statusDetected => 'Обнаружен пострадавший';

  @override
  String get lblBPM => 'УД/МИН';

  @override
  String get lblRPM => 'ДЫХ/МИН';

  @override
  String get lblDistance => 'Дистанция';

  @override
  String get lblDistanceUnit => 'м';

  @override
  String msgVictimPortalConnected(String ip) {
    return 'ПОСТРАДАВШИЙ ПОДКЛЮЧЕН! IP: $ip';
  }

  @override
  String get msgMovementDetected => 'ОБНАРУЖЕНО ДВИЖЕНИЕ!';

  @override
  String get aboutTitle => 'О приложении';

  @override
  String get aboutVersion => 'Версия 1.0.0+1';

  @override
  String get aboutDeveloper => 'Разработчик:';

  @override
  String get aboutWeb => 'Веб-сайт:';

  @override
  String get aboutContact => 'WhatsApp / Контакт:';

  @override
  String get aboutLicense => 'Лицензия:';

  @override
  String get aboutLicenseDesc => 'Тактическое использование (Moovitya)';

  @override
  String get aboutCredits => 'Кредиты:';

  @override
  String get aboutCreditsDesc => 'На основе концепции Carlos Mundaray.';

  @override
  String get btnClose => 'Закрыть';

  @override
  String get swarmTitle => 'P2P Режим Роя';

  @override
  String get swarmSubtitle => 'Тактическая сеть спасателей';

  @override
  String get swarmStatusConnected => 'ПОДКЛЮЧЕНО К СЕТИ';

  @override
  String get swarmStatusDisconnected => 'ОТКЛЮЧЕНО';

  @override
  String get btnConnectSwarm => 'Подключиться';

  @override
  String get btnDisconnectSwarm => 'Отключиться';

  @override
  String get chatInputHint => 'Тактическое сообщение...';

  @override
  String get settingsTitle => 'Настройки системы';

  @override
  String get settingsRadarSensitivity => 'Чувствительность радара';

  @override
  String get settingsAlarmSound => 'Звук тревоги';

  @override
  String get settingsVibration => 'Тактическая вибрация';

  @override
  String get mapTitle => 'Карта эвакуации';

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
