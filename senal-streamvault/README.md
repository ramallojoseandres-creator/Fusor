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

## Servidor SEÑAL (como la app anterior)

1. Pantalla de bienvenida: **usuario + contraseña** del panel  
2. `POST /api/auth/login` → M3U del **bouquet** vía `/get.php?username=&password=&type=m3u_plus`  
3. **No** se auto-carga la lista pública gigante `/downloads/lista.m3u`  
4. Listas extra opcionales + “lista pública completa (lenta)” solo manual en Setup → M3U  

Servidor: `http://185.192.20.245:3000`
