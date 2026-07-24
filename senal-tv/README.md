# SEÑAL TV

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

Versión **1.8.5** = UI/experiencia **1.8.4** + rutas del panel 2.x corregidas.

## Características

- Navegación 100% D-Pad (Leanback / Compose TV)
- Login JWT contra API SEÑAL
- Catálogo: **una descarga** `GET /api/catalog` → caché en disco; luego sin espera de red
- Playback directo desde las URLs del catálogo
- TV en vivo con categorías + paginación
- Favoritos, historial y continuar viendo (Room, locales)
- Reproductor Media3/ExoPlayer

## Rutas del panel (VPS)

Base: `http://185.192.20.245:3000/`

| Uso | Método | Ruta |
|-----|--------|------|
| Health | GET | `/api/health` |
| Login TV | POST | `/api/auth/login` |
| Catálogo | GET | `/api/catalog` |

Headers: `Authorization: Bearer <token>` + `X-Device-Id: <id>`

> `/playlist.m3u` ya no entrega la lista en el panel 2.x (sirve el admin HTML).

## Build (GitHub Actions)

1. **Actions** → **Build SEÑAL TV APK**
2. Descarga el artefacto **SenalTV-apk** → `SenalTV.apk`

## Build local

```bash
cd senal-tv
./gradlew :app:assembleTvDebug
```
