# SEÑAL Móvil — tablet / teléfono

APK táctil (`com.senal.mobile`) para Galaxy Tab, tablets y móviles.

## Por qué la tablet “no pulsaba”

La APK de TV / tablet antigua usaba `androidx.tv.material3.Surface` (Leanback).
Eso responde al mando D-pad, pero **ignora el dedo** → pantalla como bloqueada.

Esta build usa `SenalClickable` (tap real) cuando `IS_TOUCH=true`.

## Compilar

```bash
cd senal-tv
./gradlew assembleMobileRelease
# → app/build/outputs/apk/mobile/release/app-mobile-release.apk
```

Debug:

```bash
./gradlew assembleMobileDebug
```

## Instalar

1. Ajustes → permitir apps desconocidas
2. Instala `SenalMobile.apk` (paquete **com.senal.mobile**, nombre **SEÑAL Móvil**)
3. Puede coexistir con TV (`com.senal.tv`) y Tablet (`com.senal.tablet`)

## Diferencias

| | TV | Móvil |
|---|---|---|
| Leanback | Sí | No |
| Clics | D-pad | Dedo + ripple/pressed |
| Orientación | Landscape fijo | Sensor (gira) |
| Versión | 1.8.12 | **1.9.0-mobile** |
