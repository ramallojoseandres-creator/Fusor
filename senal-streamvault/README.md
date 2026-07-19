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

## Política de listas (VPS)

1. Pantalla de bienvenida: **usuario + contraseña** del panel (`POST /api/auth/login`)
2. Tras el login, la app sincroniza **solo** `http://<servidor>/downloads/lista.m3u`
3. El administrador puede subir varias listas al VPS con nombres distintos; la app **solo lee** el archivo llamado `lista.m3u`
4. En la app **no** se pueden agregar playlists ni pegar URLs M3U arbitrarias

Servidor por defecto: `http://185.192.20.245:3000`  
Lista fija: `http://185.192.20.245:3000/downloads/lista.m3u`
