# SEÑAL TV — estructura del proyecto

App Android TV nativa (Kotlin + Jetpack Compose / TV Material), estilo Flujo,
identidad **negro + azul + turquesa**. Package: `com.senal.tv`.

## Árbol principal

```
senal-tv/
├── app/build.gradle.kts          # deps + SERVER_IP / SERVER_PORT / API_BASE_URL
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/senal/tv/
│   │   ├── MainActivity.kt       # Splash → Login → Catalog → Home → Player
│   │   ├── SenalApp.kt
│   │   ├── ServerConfig.kt       # ← cambia IP/puerto aquí
│   │   ├── data/
│   │   │   ├── api/              # Retrofit SenalApi + NetworkModule
│   │   │   ├── local/            # TokenStore, PlaylistSync (M3U gzip), Room
│   │   │   ├── model/
│   │   │   └── repository/
│   │   ├── ui/
│   │   │   ├── splash/           # video raw/splash.mp4
│   │   │   ├── login/
│   │   │   ├── home/             # hub Flujo: mini-player + póster + tiles
│   │   │   ├── live/             # guía categorías | canales | video
│   │   │   ├── movies/ series/ player/ settings/ …
│   │   │   ├── components/       # focus turquesa + escala
│   │   │   └── theme/            # negro / azul / turquesa
│   │   └── util/
│   └── res/                      # mipmaps Flujo, brand_logo_pill, raw/splash
```

## Servidor

Edita `ServerConfig.kt` o `app/build.gradle.kts`:

```
SERVER_IP   = 185.192.20.245
SERVER_PORT = 3000
```

APIs usadas:
- `POST /api/auth/login`
- `GET  /playlist.m3u` (Bearer JWT)
- `GET  /api/banner`
- `GET  /api/health`

## Build

```bash
cd senal-tv
./gradlew :app:assembleTvRelease
```

APK: `app/build/outputs/apk/tv/release/`
