import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_ar.dart';
import 'app_localizations_bn.dart';
import 'app_localizations_de.dart';
import 'app_localizations_en.dart';
import 'app_localizations_es.dart';
import 'app_localizations_fr.dart';
import 'app_localizations_hi.dart';
import 'app_localizations_it.dart';
import 'app_localizations_ja.dart';
import 'app_localizations_pt.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_ur.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('ar'),
    Locale('bn'),
    Locale('de'),
    Locale('en'),
    Locale('es'),
    Locale('fr'),
    Locale('hi'),
    Locale('it'),
    Locale('ja'),
    Locale('pt'),
    Locale('ru'),
    Locale('ur'),
    Locale('zh')
  ];

  /// No description provided for @appTitle.
  ///
  /// In es, this message translates to:
  /// **'ResQRadar'**
  String get appTitle;

  /// No description provided for @tabRadar.
  ///
  /// In es, this message translates to:
  /// **'Radar'**
  String get tabRadar;

  /// No description provided for @tabSwarm.
  ///
  /// In es, this message translates to:
  /// **'Enjambre'**
  String get tabSwarm;

  /// No description provided for @tabMap.
  ///
  /// In es, this message translates to:
  /// **'Mapa'**
  String get tabMap;

  /// No description provided for @tabSettings.
  ///
  /// In es, this message translates to:
  /// **'Ajustes'**
  String get tabSettings;

  /// No description provided for @tabHistory.
  ///
  /// In es, this message translates to:
  /// **'Historial'**
  String get tabHistory;

  /// No description provided for @btnStartScan.
  ///
  /// In es, this message translates to:
  /// **'INICIAR ESCANEO'**
  String get btnStartScan;

  /// No description provided for @btnStopScan.
  ///
  /// In es, this message translates to:
  /// **'DETENER'**
  String get btnStopScan;

  /// No description provided for @statusSearching.
  ///
  /// In es, this message translates to:
  /// **'Buscando...'**
  String get statusSearching;

  /// No description provided for @statusDetected.
  ///
  /// In es, this message translates to:
  /// **'Víctima Detectada'**
  String get statusDetected;

  /// No description provided for @lblBPM.
  ///
  /// In es, this message translates to:
  /// **'BPM'**
  String get lblBPM;

  /// No description provided for @lblRPM.
  ///
  /// In es, this message translates to:
  /// **'RPM'**
  String get lblRPM;

  /// No description provided for @lblDistance.
  ///
  /// In es, this message translates to:
  /// **'Distancia'**
  String get lblDistance;

  /// No description provided for @lblDistanceUnit.
  ///
  /// In es, this message translates to:
  /// **'m'**
  String get lblDistanceUnit;

  /// No description provided for @msgVictimPortalConnected.
  ///
  /// In es, this message translates to:
  /// **'¡VÍCTIMA CONECTADA AL PORTAL! IP: {ip}'**
  String msgVictimPortalConnected(String ip);

  /// No description provided for @msgMovementDetected.
  ///
  /// In es, this message translates to:
  /// **'¡MOVIMIENTO VITAL DETECTADO!'**
  String get msgMovementDetected;

  /// No description provided for @aboutTitle.
  ///
  /// In es, this message translates to:
  /// **'Acerca de'**
  String get aboutTitle;

  /// No description provided for @aboutVersion.
  ///
  /// In es, this message translates to:
  /// **'Versión 1.0.0+1'**
  String get aboutVersion;

  /// No description provided for @aboutDeveloper.
  ///
  /// In es, this message translates to:
  /// **'Desarrollador:'**
  String get aboutDeveloper;

  /// No description provided for @aboutWeb.
  ///
  /// In es, this message translates to:
  /// **'Web:'**
  String get aboutWeb;

  /// No description provided for @aboutContact.
  ///
  /// In es, this message translates to:
  /// **'WhatsApp / Contacto:'**
  String get aboutContact;

  /// No description provided for @aboutLicense.
  ///
  /// In es, this message translates to:
  /// **'Licencia:'**
  String get aboutLicense;

  /// No description provided for @aboutLicenseDesc.
  ///
  /// In es, this message translates to:
  /// **'Uso Profesional y Rescate (Propiedad de Moovitya)'**
  String get aboutLicenseDesc;

  /// No description provided for @aboutCredits.
  ///
  /// In es, this message translates to:
  /// **'Créditos:'**
  String get aboutCredits;

  /// No description provided for @aboutCreditsDesc.
  ///
  /// In es, this message translates to:
  /// **'Basado en el concepto y sistema original creado por Carlos Mundaray.'**
  String get aboutCreditsDesc;

  /// No description provided for @btnClose.
  ///
  /// In es, this message translates to:
  /// **'Cerrar'**
  String get btnClose;

  /// No description provided for @swarmTitle.
  ///
  /// In es, this message translates to:
  /// **'Modo Enjambre P2P'**
  String get swarmTitle;

  /// No description provided for @swarmSubtitle.
  ///
  /// In es, this message translates to:
  /// **'Red de Rescatistas'**
  String get swarmSubtitle;

  /// No description provided for @swarmStatusConnected.
  ///
  /// In es, this message translates to:
  /// **'CONECTADO AL MESH'**
  String get swarmStatusConnected;

  /// No description provided for @swarmStatusDisconnected.
  ///
  /// In es, this message translates to:
  /// **'DESCONECTADO'**
  String get swarmStatusDisconnected;

  /// No description provided for @btnConnectSwarm.
  ///
  /// In es, this message translates to:
  /// **'Conectar a Mesh'**
  String get btnConnectSwarm;

  /// No description provided for @btnDisconnectSwarm.
  ///
  /// In es, this message translates to:
  /// **'Desconectar'**
  String get btnDisconnectSwarm;

  /// No description provided for @chatInputHint.
  ///
  /// In es, this message translates to:
  /// **'Mensaje de rescate...'**
  String get chatInputHint;

  /// No description provided for @settingsTitle.
  ///
  /// In es, this message translates to:
  /// **'Ajustes del Sistema'**
  String get settingsTitle;

  /// No description provided for @settingsRadarSensitivity.
  ///
  /// In es, this message translates to:
  /// **'Sensibilidad del Radar'**
  String get settingsRadarSensitivity;

  /// No description provided for @settingsAlarmSound.
  ///
  /// In es, this message translates to:
  /// **'Sonido de Alarma'**
  String get settingsAlarmSound;

  /// No description provided for @settingsVibration.
  ///
  /// In es, this message translates to:
  /// **'Vibración Háptica'**
  String get settingsVibration;

  /// No description provided for @mapTitle.
  ///
  /// In es, this message translates to:
  /// **'Mapa de Extracción'**
  String get mapTitle;

  /// No description provided for @victimModalTitle.
  ///
  /// In es, this message translates to:
  /// **'{id} - Viva - respira y pulso detectado'**
  String victimModalTitle(String id);

  /// No description provided for @victimModalLocalData.
  ///
  /// In es, this message translates to:
  /// **'Datos locales Wi-Fi'**
  String get victimModalLocalData;

  /// No description provided for @victimModalCoordX.
  ///
  /// In es, this message translates to:
  /// **'Coordenadas X'**
  String get victimModalCoordX;

  /// No description provided for @victimModalCoordY.
  ///
  /// In es, this message translates to:
  /// **'Coordenadas Y'**
  String get victimModalCoordY;

  /// No description provided for @victimModalDepth.
  ///
  /// In es, this message translates to:
  /// **'Profundidad'**
  String get victimModalDepth;

  /// No description provided for @victimModalDepthValue.
  ///
  /// In es, this message translates to:
  /// **'0,30 m bajo escombros'**
  String get victimModalDepthValue;

  /// No description provided for @victimModalHeading.
  ///
  /// In es, this message translates to:
  /// **'Rumbo'**
  String get victimModalHeading;

  /// No description provided for @victimModalProximity.
  ///
  /// In es, this message translates to:
  /// **'Proximidad'**
  String get victimModalProximity;

  /// No description provided for @victimModalProxClose.
  ///
  /// In es, this message translates to:
  /// **'MUY CERCA'**
  String get victimModalProxClose;

  /// No description provided for @victimModalProxFar.
  ///
  /// In es, this message translates to:
  /// **'LEJOS'**
  String get victimModalProxFar;

  /// No description provided for @victimModalBreathing.
  ///
  /// In es, this message translates to:
  /// **'Respirando'**
  String get victimModalBreathing;

  /// No description provided for @victimModalBreathingYes.
  ///
  /// In es, this message translates to:
  /// **'Sí - {rpm} rpm'**
  String victimModalBreathingYes(String rpm);

  /// No description provided for @victimModalHeart.
  ///
  /// In es, this message translates to:
  /// **'Corazón latiendo'**
  String get victimModalHeart;

  /// No description provided for @victimModalHeartYes.
  ///
  /// In es, this message translates to:
  /// **'Sí - {bpm} lpm'**
  String victimModalHeartYes(String bpm);

  /// No description provided for @victimModalActivity.
  ///
  /// In es, this message translates to:
  /// **'Actividad'**
  String get victimModalActivity;

  /// No description provided for @victimModalActivityYes.
  ///
  /// In es, this message translates to:
  /// **'Sí'**
  String get victimModalActivityYes;

  /// No description provided for @victimModalConfVital.
  ///
  /// In es, this message translates to:
  /// **'Confianza vital'**
  String get victimModalConfVital;

  /// No description provided for @victimModalConfDebris.
  ///
  /// In es, this message translates to:
  /// **'Confianza escombros'**
  String get victimModalConfDebris;

  /// No description provided for @msgStopScanCalibrate.
  ///
  /// In es, this message translates to:
  /// **'Detén el escaneo para calibrar.'**
  String get msgStopScanCalibrate;

  /// No description provided for @msgNoiseFloor.
  ///
  /// In es, this message translates to:
  /// **'Noise Floor: {noise} dBm'**
  String msgNoiseFloor(String noise);

  /// No description provided for @titleConnectedDevices.
  ///
  /// In es, this message translates to:
  /// **'Dispositivos Conectados'**
  String get titleConnectedDevices;

  /// No description provided for @historyTitle.
  ///
  /// In es, this message translates to:
  /// **'Historial de Búsquedas'**
  String get historyTitle;

  /// No description provided for @historyEmpty.
  ///
  /// In es, this message translates to:
  /// **'No hay rescates registrados.'**
  String get historyEmpty;

  /// No description provided for @historyRescue.
  ///
  /// In es, this message translates to:
  /// **'Rescate #{id}'**
  String historyRescue(String id);

  /// No description provided for @historyDate.
  ///
  /// In es, this message translates to:
  /// **'Fecha: {date}'**
  String historyDate(String date);

  /// No description provided for @historyDuration.
  ///
  /// In es, this message translates to:
  /// **'Duración: {sec}s'**
  String historyDuration(String sec);

  /// No description provided for @historyDetections.
  ///
  /// In es, this message translates to:
  /// **'Detecciones: {count}'**
  String historyDetections(String count);

  /// No description provided for @historyDetailTitle.
  ///
  /// In es, this message translates to:
  /// **'Detalle de Rescate #{id}'**
  String historyDetailTitle(String id);

  /// No description provided for @historyDetailVictims.
  ///
  /// In es, this message translates to:
  /// **'Detalle de Víctimas:'**
  String get historyDetailVictims;

  /// No description provided for @historyVictim.
  ///
  /// In es, this message translates to:
  /// **'Víctima: {id}'**
  String historyVictim(String id);

  /// No description provided for @historyHeart.
  ///
  /// In es, this message translates to:
  /// **'Ritmo Cardíaco: {bpm} BPM'**
  String historyHeart(String bpm);

  /// No description provided for @historyBreath.
  ///
  /// In es, this message translates to:
  /// **'Respiración: {rpm} RPM'**
  String historyBreath(String rpm);

  /// No description provided for @historyError.
  ///
  /// In es, this message translates to:
  /// **'Error al leer datos: {err}'**
  String historyError(String err);

  /// No description provided for @settingsCancel.
  ///
  /// In es, this message translates to:
  /// **'Cancelar'**
  String get settingsCancel;

  /// No description provided for @settingsSave.
  ///
  /// In es, this message translates to:
  /// **'Guardar'**
  String get settingsSave;

  /// No description provided for @settingsMinFreq.
  ///
  /// In es, this message translates to:
  /// **'Frecuencia Mínima de Respiración'**
  String get settingsMinFreq;

  /// No description provided for @settingsMaxFreq.
  ///
  /// In es, this message translates to:
  /// **'Frecuencia Máxima de Respiración'**
  String get settingsMaxFreq;

  /// No description provided for @settingsMotionCancel.
  ///
  /// In es, this message translates to:
  /// **'Cancelación de Movimiento'**
  String get settingsMotionCancel;

  /// No description provided for @settingsMotionCancelDesc.
  ///
  /// In es, this message translates to:
  /// **'Usa el acelerómetro para reducir ruido'**
  String get settingsMotionCancelDesc;

  /// No description provided for @settingsNightVision.
  ///
  /// In es, this message translates to:
  /// **'Modo Visión Nocturna'**
  String get settingsNightVision;

  /// No description provided for @settingsNightVisionDesc.
  ///
  /// In es, this message translates to:
  /// **'Alto contraste para operaciones con poca luz'**
  String get settingsNightVisionDesc;

  /// No description provided for @settingsBgScan.
  ///
  /// In es, this message translates to:
  /// **'Escaneo en Segundo Plano'**
  String get settingsBgScan;

  /// No description provided for @settingsBgScanDesc.
  ///
  /// In es, this message translates to:
  /// **'Continúa escaneando al apagar la pantalla'**
  String get settingsBgScanDesc;

  /// No description provided for @swarmRestart.
  ///
  /// In es, this message translates to:
  /// **'Reiniciar Búsqueda P2P'**
  String get swarmRestart;

  /// No description provided for @swarmStatus.
  ///
  /// In es, this message translates to:
  /// **'Estado: {status}'**
  String swarmStatus(String status);
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) => <String>[
        'ar',
        'bn',
        'de',
        'en',
        'es',
        'fr',
        'hi',
        'it',
        'ja',
        'pt',
        'ru',
        'ur',
        'zh'
      ].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'ar':
      return AppLocalizationsAr();
    case 'bn':
      return AppLocalizationsBn();
    case 'de':
      return AppLocalizationsDe();
    case 'en':
      return AppLocalizationsEn();
    case 'es':
      return AppLocalizationsEs();
    case 'fr':
      return AppLocalizationsFr();
    case 'hi':
      return AppLocalizationsHi();
    case 'it':
      return AppLocalizationsIt();
    case 'ja':
      return AppLocalizationsJa();
    case 'pt':
      return AppLocalizationsPt();
    case 'ru':
      return AppLocalizationsRu();
    case 'ur':
      return AppLocalizationsUr();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
