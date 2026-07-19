# VitalFi VE / ResQRadar — uso en brigadas

## Qué incluye este fork

| App | Plataforma | Capacidad Wi‑Fi / radar |
|-----|------------|-------------------------|
| **VitalFi VE** (`android/`) | Android (APK) | Completa: RSSI, DSP, brújula, CSV, ML opcional |
| **ResQRadar Flutter** (`flutter_app/`) | Android + iOS | UI de coordinación; Wi‑Fi solo en Android |

Crédito del software original: **Carlos Mundaray / Solvitco** — [vitalfiappsweb](https://github.com/correangel/vitalfiappsweb) (MIT).

## Android (recomendado en campo)

1. Instalar `VitalFi-VE-1.4.0.apk`.
2. Conceder **ubicación** (Android lo exige para escanear Wi‑Fi).
3. Primera apertura: leer la **guía de brigada**.
4. Activar **Detección de firmas** y, si hay modelo, **ML**.
5. Exportar CSV desde el menú para compartir con el puesto de mando.

## iOS — límites importantes

Apple **no permite** a apps de App Store / sideload normales:

- escanear BSSID / RSSI de redes Wi‑Fi ajenas,
- hacer “radar” equivalente al de Android.

Por eso el IPA Flutter sirve como **app de coordinación** (listas, estado, mismo branding), no como detector de víctimas por Wi‑Fi.

Para un IPA firmado hace falta **cuenta Apple Developer** + Mac con Xcode:

```bash
cd flutter_app
flutter pub get
flutter build ipa   # o abrir ios/Runner.xcworkspace en Xcode
```

## Entrenar el modelo TFLite (opcional)

```bash
cd python
# seguir README / train_model.py del upstream
# copiar el .tflite a android/app/src/main/assets/signature_model.tflite
# y a flutter_app/assets/models/model_vitalfi.tflite
```

Sin modelo, el detector DSP sigue funcionando.