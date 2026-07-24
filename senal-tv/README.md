# SEÑAL TV

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

## Características

- Navegación 100% D-Pad (Leanback / Compose TV)
- Login JWT contra API REST SEÑAL (`X-Device-Id` en todas las peticiones)
- Validación de sesión con `GET /api/me`
- Catálogo remoto autenticado (`GET /api/catalog`)
- TV en vivo con categorías + paginación
- Películas y series con lazy loading
- Búsqueda, favoritos, historial y continuar viendo (locales)
- Reproductor Media3/ExoPlayer (URL de catálogo o `/api/playback`)
- Solo landscape · Android 7+ (API 24)

## Seguridad

No almacena usuario/contraseña Xtream. Solo JWT de sesión, `deviceId`, preferencias, favoritos e historial.

## API (VPS)

Base: `http://185.192.20.245:3000/` (`BuildConfig.API_BASE_URL`)

| Uso | Método | Endpoint |
|-----|--------|----------|
| Health | GET | `/api/health` |
| Login TV | POST | `/api/auth/login` |
| Sesión | GET | `/api/me` |
| Contenido | GET | `/api/catalog` |

Headers TV: `Authorization: Bearer <token>` + `X-Device-Id: <id>`

Login body:

```json
{
  "username": "usuario",
  "password": "clave",
  "deviceId": "<id>",
  "deviceName": "SEÑAL TV",
  "platform": "android-tv"
}
```

## Build (GitHub Actions)

1. Abre **Actions** → **Build SEÑAL TV APK** (o descarga desde **Releases**)
2. Si usas Actions: descarga el artefacto **SenalTV-apk**, **descomprime el ZIP** y usa solo `SenalTV.apk`
3. No instales el `.zip` en el TV (provoca “error durante el análisis del paquete”)

Preferible: **Releases** → descarga directa de `SenalTV.apk`

## Build local

```bash
cd senal-tv
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
