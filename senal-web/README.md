# SEÑAL Web

Versión navegador de SEÑAL — mismo look que el APK 1.8.x / móvil:

- Splash con wordmark **SEÑAL** (Ñ cyan)
- Login contra el VPS (`api/auth/login` + `deviceId`)
- Home con tiles VIVO / PELÍCULAS / SERIES / AJUSTES
- Guía en vivo con las mismas categorías preferidas
- Películas / Series Daniel65 por categoría (sidebar + lista, como en TV)
- Player HLS (`hls.js`)

## Arrancar

```bash
cd senal-web
npm install
npm run dev
# → http://localhost:5173
```

Build estático:

```bash
npm run build
# → dist/  (servir con nginx / VPS)
```

## Config

Por defecto apunta a `http://185.192.20.245:3000`.

```bash
VITE_API_BASE=http://185.192.20.245:3000 npm run dev
```

Catálogos embebidos:

- `public/catalog/lista_fusionada.m3u.gz` — en vivo (misma lista del APK)
- `public/vod/daniel65_peliculas.m3u.gz` / `daniel65_series.m3u.gz` — VOD Daniel65

## Nota streams

Algunos canales fallan en el navegador por **CORS** del origen del stream.
En tablet/TV nativo no aplica. Para web al 100% hace falta un proxy HLS en el VPS.
