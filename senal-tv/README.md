# SEÑAL TV

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

## Características

- Identidad visual SEÑAL (sin logos ni colores de terceros)
- Login JWT contra API REST SEÑAL (`X-Device-Id` en todas las peticiones)
- Catálogo cargado **una vez** (memoria + disco) y canales al instante
- TV en vivo, películas, series, búsqueda, favoritos
- Reproductor Media3/ExoPlayer (usa URL del catálogo sin esperar `/api/playback`)
- Solo landscape · Android 7+ (API 24)

## API (VPS)

Base: `http://185.192.20.245:3000/`

| Uso | Método | Endpoint |
|-----|--------|----------|
| Health | GET | `/api/health` |
| Login TV | POST | `/api/auth/login` |
| Sesión | GET | `/api/me` |
| Contenido | GET | `/api/catalog` |

Headers: `Authorization: Bearer <token>` + `X-Device-Id: <id>`

## Build

```bash
cd senal-tv
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` · versión `2.1.0`
