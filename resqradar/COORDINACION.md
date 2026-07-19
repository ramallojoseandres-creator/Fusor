# VitalFi VE / ResQRadar — uso en brigadas

## Qué incluye este fork

| App | Plataforma | Capacidad Wi‑Fi / radar |
|-----|------------|-------------------------|
| **VitalFi VE** (`android/`) | Android (APK) | Completa: RSSI, DSP, brújula, CSV, ML opcional |
| **ResQRadar Flutter** (`flutter_app/`) | Android + iOS | UI de coordinación; Wi‑Fi solo en Android |

Crédito del software original: **Carlos Mundaray / Solvitco** — [vitalfiappsweb](https://github.com/correangel/vitalfiappsweb) (MIT).

## Android (recomendado en campo)

1. Instalar `VitalFi-VE-1.4.0.apk` (en `releases/` o artefacto del agente).
2. Conceder **ubicación** (Android lo exige para escanear Wi‑Fi).
3. Primera apertura: leer la **guía de brigada**.
4. Activar **Detección de firmas** y, si hay modelo, **ML**.
5. Exportar CSV desde el menú para compartir con el puesto de mando.

## iOS + LiveContainer (ruta principal)

Apple **no permite** escanear BSSID / RSSI de redes Wi‑Fi ajenas. En iPhone esta app es de **coordinación** (mapa, historial, enjambre), no detector de víctimas por Wi‑Fi.

Si tienes **LiveContainer** (como SEÑAL):

1. En GitHub Actions corre el workflow **Build ResQRadar iOS IPA (LiveContainer)** (`workflow_dispatch` o push a `flutter_app/`).
2. Descarga el artefacto / release `ResQRadar-VE-LiveContainer.ipa`.
3. En LiveContainer: **Settings → Import Certificate** desde AltStore/SideStore (modo JIT-Less).
4. **My Apps → +** → elige el IPA → ábrela desde LiveContainer.

LiveContainer **re-firma** el IPA; no hace falta cuenta Apple Developer de pago.

Build local (Mac):

```bash
cd flutter_app
flutter pub get
flutter build ios --release --no-codesign
# empaquetar Payload/Runner.app → ResQRadar-VE-LiveContainer.ipa
```

## Entrenar el modelo TFLite (opcional)

```bash
cd python
# seguir README / train_model.py del upstream
# copiar el .tflite a android/app/src/main/assets/signature_model.tflite
# y a flutter_app/assets/models/model_vitalfi.tflite
```

Sin modelo, el detector DSP (Android) sigue funcionando.
