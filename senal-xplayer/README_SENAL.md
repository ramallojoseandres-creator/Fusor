# SEÑAL XPlayer

Fork de [TNT-Likely/xplayer](https://github.com/TNT-Likely/xplayer) (MIT) rebranded for **SEÑAL**.

- Marca: SEÑAL · frase: *Tu ventana al mundo*
- Color: teal `#00E5C8`
- Lista por defecto: `http://185.192.20.245:3000/downloads/lista.m3u`
- `applicationId`: `com.senal.xplayer`
- Android TV: `LEANBACK_LAUNCHER`

## Build

```sh
cd senal-xplayer
flutter pub get
flutter build apk --release -PabiSplit
```

Copyright original XPlayer: TNT-Likely (MIT). Modifications: SEÑAL branding + VPS playlist.
