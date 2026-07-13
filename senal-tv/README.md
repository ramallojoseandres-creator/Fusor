# SEÑAL TV

Aplicación IPTV premium para **Android TV**, **Google TV**, **Fire TV Stick**, **Nvidia Shield** y **Xiaomi TV Box**.

## Características

- Navegación 100% D-Pad (Leanback / Compose TV)
- Login JWT contra API REST SEÑAL
- TV en vivo con categorías + paginación infinita
- Películas y series con lazy loading
- Búsqueda instantánea
- Favoritos, historial y continuar viendo (locales, sin duplicados)
- Reproductor Media3/ExoPlayer con overlay moderno, CH+/CH−, audio, subtítulos, aspecto, velocidad y reconexión automática
- Solo landscape · Android 7+ (API 24)
- Cache de logos/posters (Coil) y de categorías/EPG

## Seguridad

No almacena usuario/contraseña Xtream. Solo JWT, preferencias, favoritos e historial.

## API

Base URL configurable en `BuildConfig.API_BASE_URL` (por defecto `http://185.192.20.245:3000/`).

La app consume:

- `POST /api/auth/login`
- `GET /api/catalog?type=live|movie|series` (compatible con el servidor actual)
- `GET /api/search`
- `POST /api/playback/{id}`

Favoritos / historial / continuar se sincronizan localmente si el backend aún no expone esas rutas.

## Build (GitHub Actions)

En cada push/PR sobre `senal-tv/**` (o con **Actions → Build SEÑAL TV APK → Run workflow**):

1. Abre la pestaña **Actions** del repo
2. Entra en el run **Build SEÑAL TV APK**
3. Descarga el artefacto **SenalTV-debug** (`app-debug.apk`)

## Build local

```bash
cd senal-tv
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
