# SEÑAL TV

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

## Características

- Navegación 100% D-Pad (Leanback / Compose TV)
- Login JWT contra API SEÑAL **solo para usuarios** (alta / sesión)
- Catálogo **embebido** desde `lista_fusionada.m3u` (no viaja servidor → app)
- Playback directo desde las URLs de la lista (app → stream CDN)
- TV en vivo con categorías + paginación
- Películas / series 24/7 (grupos de la lista)
- Búsqueda local instantánea
- Favoritos, historial y continuar viendo (Room, locales)
- Reproductor Media3/ExoPlayer con overlay, CH+/CH−, audio, subtítulos, User-Agent de la lista
- Solo landscape · Android 7+ (API 24)

## Arquitectura de datos

```
Servidor SEÑAL  →  solo POST /api/auth/login (JWT)
APK assets      →  catalog/lista_fusionada.m3u.gz  (~9.8k canales)
Cliente         →  ExoPlayer abre la URL del canal directamente
```

No se llama a `/api/catalog`, `/api/search` ni `/api/playback`.

## Seguridad

No almacena usuario/contraseña Xtream. Solo JWT de sesión, preferencias, favoritos e historial.

## API (auth)

Base URL: `BuildConfig.API_BASE_URL` → `http://185.192.20.245:3000/`

- `POST /api/auth/login` `{username, password, deviceId, deviceName}`

## Actualizar la lista embebida

Cuando `fusor.sh` regenera `lista_fusionada.m3u` en la raíz del repo:

```bash
gzip -c -9 lista_fusionada.m3u > senal-tv/app/src/main/assets/catalog/lista_fusionada.m3u.gz
```

Luego rebuild del APK.

## Build (GitHub Actions)

1. **Actions** → **Build SEÑAL TV APK** (o **Releases**)
2. Si usas Actions: descarga el artefacto **SenalTV-apk**, descomprime el ZIP y usa `SenalTV.apk`
3. No instales el `.zip` en el TV

## Build local

```bash
cd senal-tv
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
