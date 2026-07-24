# SEÑAL Web

Versión navegador de SEÑAL — mismo look que el APK 1.8.x / móvil:

- Splash con wordmark **SEÑAL** (Ñ cyan)
- Login contra el VPS (`api/auth/login` + `deviceId`)
- Home con tiles VIVO / PELÍCULAS / SERIES / AJUSTES
- Guía en vivo con las mismas categorías preferidas
- Películas / Series Daniel65 por categoría (sidebar + lista, como en TV)
- Player HLS (`hls.js`)

## Arrancar (dev)

```bash
cd senal-web
npm install
npm run dev
# → http://localhost:5173
```

## Publicar en el mismo VPS (`public/web`)

Pensado para uso personal en tu IP: `http://185.192.20.245:3000/web/`

### 1) Una vez en el Express del panel

```js
const path = require('path')
// …

app.use('/web', express.static(path.join(__dirname, 'public/web')))
app.get(/^\/web(\/.*)?$/, (_req, res) => {
  res.sendFile(path.join(__dirname, 'public/web', 'index.html'))
})
```

(No pisa `/api`, `/dowloads` ni el admin.)

### 2) Build + copiar

```bash
cd senal-web
npm install
# local (prueba):
./publish-web.sh
# o directo al panel:
./publish-web.sh /ruta/del/panel/public/web
# o por scp:
./publish-web.sh root@185.192.20.245:/ruta/del/panel/public/web
```

Abre: **http://185.192.20.245:3000/web/**

En producción el login usa la misma origen (`/api/...`), sin CORS raro.

Si quieres la web en la raíz de `public/` en vez de `/web/`:

```bash
VITE_BASE=/ ./publish-web.sh /ruta/del/panel/public
```

## Config

- Dev: API por defecto `http://185.192.20.245:3000`
- Build (`/web/`): `API_BASE` vacío → mismo host

```bash
VITE_API_BASE=http://185.192.20.245:3000 npm run dev
```

Catálogos embebidos (van dentro de `dist/` / `public/web/`):

- `catalog/lista_fusionada.m3u.gz` — en vivo
- `vod/daniel65_peliculas.m3u.gz` / `daniel65_series.m3u.gz` — VOD Daniel65

## Nota streams

Algunos canales fallan en el navegador por **CORS** del origen del stream.
En tablet/TV nativo no aplica. Para web al 100% hace falta un proxy HLS en el VPS.
