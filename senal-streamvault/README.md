# SEÑAL TV (StreamVault UI)

Cliente IPTV Android TV con el diseño de **StreamVault**, rebrandeado como **SEÑAL**.

## Créditos y licencia (obligatorio)

**Based on StreamVault** by **David Nashash (Davidona)**.

- Original: https://github.com/Davidona/StreamVault-IPTV  
- Mirror: https://github.com/correangel/StreamVault-IPTV  
- Support: https://ko-fi.com/davidona  
- License: StreamVault Source-Available License (**non-commercial**) — see `LICENSE`

Este fork **no puede venderse** ni usarse como producto de pago sin permiso escrito del autor original.

## Build

```bash
cd senal-streamvault
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/
```

## Uso

Al abrir: Welcome → Setup Provider → M3U / Xtream / Stalker → sync → Live / Movies / Series.

## Servidor SEÑAL

Por defecto conecta a `http://185.192.20.245:3000`:

- **SEÑAL Principal** → `/downloads/lista.m3u` (auto al primer arranque)
- **SEÑAL (mi cuenta)** → `/get.php?username=&password=&type=m3u_plus`
- **Lista extra 1/2** → opcionales (`senal.extra1.url` / `senal.extra2.url` en `local.properties`)
