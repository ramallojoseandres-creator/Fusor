# SEÑAL Tablet — Galaxy Tab A8 (SM-X200)

APK dedicada para **Samsung Galaxy Tab A8 10.5" (SM-X200 / SM-X205)**.

## Qué cambia vs la APK de TV

| | TV (`com.senal.tv`) | Tablet X200 (`com.senal.tablet`) |
|---|---|---|
| Leanback (Android TV) | Obligatorio | **No** (instalable en tablet) |
| Icono launcher | TV + Leanback | Solo launcher táctil |
| Autoplay último canal | Sí | **No** (tú eliges EN VIVO) |
| Targets táctiles | Enfoque D-pad | Filas/tiles más altos |
| Orientación | Landscape fijo | `sensorLandscape` |
| ABI | Todas | `armeabi-v7a` + `arm64-v8a` (Unisoc T618) |
| Versión | 1.10.x | **1.20.0-x200** |

## Compilar

```bash
cd senal-tv
./gradlew assembleTabletRelease
# → app/build/outputs/apk/tablet/release/app-tablet-release.apk
```

## Instalar en el Tab A8

1. Ajustes → Seguridad → permitir orígenes desconocidos / instalar apps desconocidas
2. Copia `SenalTV-Tablet-X200.apk` a la tablet
3. Ábrelo e instala (paquete `com.senal.tablet`, nombre **SEÑAL Tablet**)

Puede coexistir con la APK de TV (`com.senal.tv`).
