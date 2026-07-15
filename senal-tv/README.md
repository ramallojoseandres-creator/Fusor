# SEÑAL TV 2.0

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

## Características

- Navegación 100% D-Pad (Leanback / Compose TV)
- Login JWT contra API SEÑAL
- **Catálogo remoto rápido** (`/api/catalog/fast` JSON.gz + ETag) — **sin M3U dentro del APK**
- Cache local → categorías al instante al abrir EN VIVO (no esperan al player)
- Guía EN VIVO: categorías | canales **sobre** el reproductor
- Botones home gruesos (estilo brutalista / otra tipografía)
- Playback directo (ExoPlayer / Media3) desde las URLs del catálogo
- Favoritos, historial y continuar viendo (Room)
- Solo landscape · Android 7+ (API 24)

## Arquitectura de datos

```
Panel SEÑAL 3.1  →  importa/ordena/renombra → genera catalog.fast.json.gz
APK             →  GET /api/catalog/fast (+ If-None-Match) → cache en disco
EN VIVO         →  pinta categorías YA; el player sintoniza en paralelo
Streams         →  ExoPlayer abre la URL del canal directamente
```

## Seguridad

No almacena usuario/contraseña Xtream. Solo JWT de sesión, preferencias, favoritos e historial.

## API

Base URL: `BuildConfig.API_BASE_URL` → `http://185.192.20.245:3000/`

- `POST /api/auth/login`
- `GET /api/banner`
- `GET /api/catalog/meta`
- `GET /api/catalog/fast` (JSON gzip, ETag)

## Actualizar el catálogo (ya no se empaqueta en el APK)

1. Importa o edita en el **panel** (`senal-server`)
2. El servidor regenera `data/catalog.fast.json.gz`
3. Las apps refrescan con ETag (304 si no hay cambios)

## Build (GitHub Actions)

1. **Actions** → **Build SEÑAL TV APK**
2. Descarga el artefacto **SenalTV-apk**

## Build local

```bash
cd senal-tv
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
