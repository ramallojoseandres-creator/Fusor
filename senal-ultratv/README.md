# SEÑAL TV (Ultra TV fork)

Cliente IPTV Android TV basado en **[Ultra TV](https://github.com/khalilbenaz/ultra-tv)** (MIT), rebrandeado como **SEÑAL** y conectado al VPS del panel.

## Créditos

Based on **Ultra TV** by [khalilbenaz](https://github.com/khalilbenaz/ultra-tv) — MIT License (see `LICENSE`).

## Política SEÑAL

1. Login: `POST http://185.192.20.245:3000/api/auth/login`
2. Contenido: solo `http://185.192.20.245:3000/downloads/lista.m3u`
3. No se pueden agregar playlists ni fuentes en la app
4. Telemetría / auto-update de Ultra TV desactivados

## Build

```bash
cd senal-ultratv/android-native
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/
# applicationId: com.senal.ultratv
```

## Install (Mac → TV)

```bash
adb connect 192.168.x.x:PORT
adb install -r senal-ultratv-1.0.0.apk
```
