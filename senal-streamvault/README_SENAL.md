# SEÑAL v1

App IPTV basada en [StreamVault-IPTV](https://github.com/Davidona/StreamVault-IPTV) (David Nashash).

## Esta versión
- Nombre: **SEÑAL** · slogan *Tu ventana al mundo* · **v1**
- Sin inicio de sesión de panel
- Solo carga de **lista M3U por URL o archivo**
- URL de prueba prellenada: `http://185.192.20.245:3000/downloads/lista.m3u`
- `applicationId`: `com.senal.v1`

## Build
```bash
cd senal-streamvault
./gradlew :app:assembleRelease
```

## Próximo
Login + catálogo desde el server SEÑAL (tras validar esta build).
