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

Si tienes **LiveContainer** (igual que SEÑAL):

1. Genera el IPA (CI o Mac):
   - GitHub Actions: workflow **Build ResQRadar iOS IPA (LiveContainer)** → artifact `ResQRadar-VE-LiveContainer.ipa`
   - O en Mac: `bash scripts/package_livecontainer_ipa.sh`
2. LiveContainer → **Settings → Import Certificate** (AltStore/SideStore, JIT-Less).
3. **My Apps → +** → elige el IPA → ábrela desde LiveContainer.

LiveContainer **re-firma** el IPA; no hace falta Apple Developer de pago.

> Si Actions falla al instante con *spending limit / payments*, hay que subir el límite de billing de GitHub (macOS runners de pago) o generar el IPA en un Mac.

## Entrenar el modelo TFLite (opcional)

```bash
cd python
# seguir README / train_model.py del upstream
# copiar el .tflite a android/app/src/main/assets/signature_model.tflite
# y a flutter_app/assets/models/model_vitalfi.tflite
```

Sin modelo, el detector DSP (Android) sigue funcionando.
