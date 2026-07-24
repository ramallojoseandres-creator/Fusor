# SEÑAL Tablet — Galaxy Tab A8 (SM-X200)

APK dedicada para **Samsung Galaxy Tab A8 10.5" (SM-X200 / SM-X205)**.

> **Nota 1.20.2:** ahora usa botones táctiles reales (`SenalClickable`).
> La 1.20.1-x200 podía quedar “congelada” porque heredaba la UI Leanback de TV.
> Si prefieres el paquete genérico: instala también **SEÑAL Móvil** (`com.senal.mobile`).

## Qué cambia vs la APK de TV

| | TV (`com.senal.tv`) | Tablet X200 (`com.senal.tablet`) |
|---|---|---|
| Leanback (Android TV) | Obligatorio | **No** (instalable en tablet) |
| Icono launcher | TV + Leanback | Solo launcher táctil |
| Clics | D-pad | **Dedo** |
| Autoplay último canal | No | **No** (tú eliges EN VIVO) |
| Targets táctiles | Enfoque D-pad | Filas/tiles más altos |
| Orientación | Landscape fijo | `sensor` |
| ABI | ARM | `armeabi-v7a` + `arm64-v8a` (Unisoc T618) |
| Versión | 1.8.x | **1.20.2-x200** |

## Compilar

```bash
cd senal-tv
./gradlew assembleTabletRelease
# → app/build/outputs/apk/tablet/release/app-tablet-release.apk
```

## Instalar en el Tab A8

1. Ajustes → Seguridad → permitir orígenes desconocidos / instalar apps desconocidas
2. Copia el APK a la tablet
3. Ábrelo e instala (paquete `com.senal.tablet`, nombre **SEÑAL Tablet**)

Puede coexistir con la APK de TV (`com.senal.tv`) y Móvil (`com.senal.mobile`).
