# VitalFi VE / ResQRadar — fork colaborativo

Herramienta experimental de apoyo a brigadas de rescate: analiza variaciones de **Wi‑Fi RSSI** para estimar presencia/actividad bajo escombros y mostrar rumbo en un radar.

> **Aviso:** estimaciones, no 100 % confiables. No reemplaza equipos ni protocolos oficiales de búsqueda y rescate.

## Descargas (GitHub Release)

- [VitalFi-VE-1.4.0.apk](https://github.com/ramallojoseandres-creator/Fusor/releases/download/vitalfi-ve-1.4.0/VitalFi-VE-1.4.0.apk) — radar Wi‑Fi (recomendado)
- [ResQRadar-VE-1.4.0.apk](https://github.com/ramallojoseandres-creator/Fusor/releases/download/vitalfi-ve-1.4.0/ResQRadar-VE-1.4.0.apk) — Flutter
- Release: https://github.com/ramallojoseandres-creator/Fusor/releases/tag/vitalfi-ve-1.4.0

## Origen y licencia

- Base original **VitalFi / WhoFi** de **Carlos Mundaray — Solvitco**, licenciada **MIT** (ver `LICENSE`).
- Código tomado de [correangel/vitalfiappsweb](https://github.com/correangel/vitalfiappsweb).
- Evolución Flutter (ResQRadar) incluida en `flutter_app/` (proyecto completado para Android/iOS).
- Este fork **VitalFi VE 1.4.0** añade guía de campo para brigadas y empaquetado multiplataforma.

## Estructura

```
resqradar/
├── android/          # App nativa Kotlin + Compose (VitalFi VE)
├── flutter_app/      # App Flutter ResQRadar (APK + IPA)
├── python/           # Simulación / entrenamiento TFLite
├── docs/             # Paper WhoFi
└── LICENSE           # MIT — Carlos Mundaray / Solvitco
```

## APKs listos (releases/)

| Archivo | App | Uso |
|---------|-----|-----|
| `releases/VitalFi-VE-1.4.0.apk` | Kotlin nativo | **Campo / radar Wi‑Fi** (recomendado) |
| `releases/ResQRadar-VE-1.4.0.apk` | Flutter | UI multi‑idioma + mapa / enjambre |

## Android nativo (recomendado para campo)

```bash
cd android
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Requisitos: JDK 17+, Android SDK 34.

### Mejoras VE 1.4.0

- Guía de campo al primer uso (router 2.4 GHz, giro 360°, avisos de falsos positivos).
- Nombre/versión `VitalFi VE 1.4.0-ve`.
- Firma release con keystore si existe; si no, firma debug (builds de prueba).

## Flutter (APK + IPA)

```bash
cd flutter_app
flutter pub get
flutter build apk --release
flutter build ios --release   # requiere macOS + Xcode
```

### iOS + LiveContainer

Apple **no permite** escanear redes Wi‑Fi/RSSI como Android. La IPA es **coordinación de brigada**.

Con **LiveContainer** (misma ruta que SEÑAL):

1. Workflow GitHub: `Build ResQRadar iOS IPA (LiveContainer)` → artefacto `ResQRadar-VE-LiveContainer.ipa`
2. LiveContainer → importar certificado (JIT-Less) → **+** → IPA

```bash
flutter build ios --release --no-codesign   # Mac / CI
```

El radar Wi‑Fi completo sigue siendo prioridad Android (`VitalFi-VE-*.apk`).

## Créditos

- **Carlos Mundaray / Solvitco** — núcleo VitalFi (MIT)
- Comunidad / fork VE — mejoras de campo y empaquetado
EOF