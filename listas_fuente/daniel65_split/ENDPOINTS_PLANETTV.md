# PlanetTV / Daniel65 — endpoints y link final de TV

Panel **Xtream Codes** detrás de:

| | |
|---|---|
| Host | `planettvweb.com` |
| Puerto HTTP | `8091` |
| HTTPS | `25463` |
| RTMP | `25462` |
| Usuario | el del M3U (`Daniel65`) |
| Password | el del M3U (`BMad5394d`) |

Mensaje del panel: *«NO TE OLVIDES DE USAR EL VPN SIEMPRE»* — sin VPN el load-balancer a veces responde vacío/`403`.

---

## 1) API de catálogo (JSON)

Base: `http://planettvweb.com:8091/player_api.php?username=USER&password=PASS`

| Acción | Uso |
|---|---|
| *(sin action)* | `user_info` + `server_info` (auth, expira, max_connections=3, formatos `m3u8/ts/rtmp`) |
| `action=get_live_categories` | Categorías TV |
| `action=get_live_streams` | Canales + `stream_id` + logo + `category_id` |
| `action=get_vod_categories` | Categorías películas |
| `action=get_vod_streams` | Películas + `stream_id` |
| `action=get_vod_info&vod_id=ID` | Detalle película (`container_extension`, TMDB…) |
| `action=get_series_categories` | Categorías series |
| `action=get_series` | Series + `series_id` |
| `action=get_series_info&series_id=ID` | Temporadas/episodios (`episodes[season][].id`) |
| `action=get_short_epg&stream_id=ID` | EPG corto |
| `panel_api.php?username=&password=` | Bundle grande (user + categories +…) |
| `xmltv.php?username=&password=` | EPG XMLTV |
| `get.php?username=&password=&type=m3u_plus&output=ts\|m3u8` | Descarga el M3U completo |

Ejemplo live item:

```json
{
  "name": "CANAL 4",
  "stream_type": "live",
  "stream_id": 14181,
  "stream_icon": "…",
  "category_id": "83",
  "direct_source": ""
}
```

`direct_source` vacío ⇒ el stream pasa por el panel (no es URL CDN directa en el JSON).

---

## 2) URLs de reproducción (entrada)

Construidas con `USER`, `PASS` y el id:

| Tipo | URL de entrada (la del M3U / la que debe abrir el player) |
|---|---|
| **Live (como en el M3U)** | `http://planettvweb.com:8091/USER/PASS/{stream_id}` |
| **Live HLS (recomendado ExoPlayer)** | `http://planettvweb.com:8091/live/USER/PASS/{stream_id}.m3u8` |
| **Live TS** | `http://planettvweb.com:8091/live/USER/PASS/{stream_id}.ts` |
| **Película** | `http://planettvweb.com:8091/movie/USER/PASS/{vod_id}.{ext}` |
| **Capítulo serie** | `http://planettvweb.com:8091/series/USER/PASS/{episode_id}.{ext}` |

Extensiones típicas: movie `mkv`, series `mp4` (viene en `container_extension`).

---

## 3) Cómo se obtiene el **link final** de un canal

Cadena real observada:

```
1) Player pide:
   GET /live/USER/PASS/{id}.m3u8
   User-Agent: Lavf/*  o  ExoPlayer   (importante)

2) Panel responde 302 Location:
   http://{LB_IP}:25461/live/USER/PASS/{id}.m3u8?token=XXXX

3) Load-balancer devuelve playlist HLS (#EXTM3U)
   segmentos relativos tipo:
   /hlsr/{token_fragment}/… .ts

4) Segmentos absolutos:
   http://{LB_IP}:25461/hlsr/… .ts
```

Eso **es** el link final de transmisión: no hay otra URL “oculta” en la API; el CDN/LB se revela en el `Location` + playlist.

### User-Agent crítico
| UA | Resultado en LB |
|---|---|
| `Lavf/…` (ffmpeg) | HLS OK |
| `ExoPlayer` | HLS OK |
| MAG200 / VLC “browser” | a menudo vacío / HTML |

En SEÑAL/ExoPlayer ya suele ir bien si se siguen redirects.

### VPN / IP
Sin la VPN del proveedor, el LB puede devolver `200` vacío o `403` aunque el `302` sea correcto.

---

## 4) Flujo práctico para SEÑAL

1. Auth/catálogo: `player_api.php` → `get_live_streams` (o usar el M3U).
2. Para sintonizar canal `stream_id`:
   - Preferir `…/live/USER/PASS/{id}.m3u8`
   - ExoPlayer con `allowCrossProtocolRedirects` / follow redirects.
3. Opcional (más control): HEAD/GET sin follow → leer `Location` → reproducir esa URL con el mismo UA.
4. EPG: `xmltv.php` o `get_short_epg`.

---

## 5) Otros hosts en el M3U de canales

No todo es PlanetTV (~1014/1028 sí lo son):

- Pluto TV HLS públicos (`stitcher-ipv4.pluto.tv/…/master.m3u8`)
- `ixter.me` (otro Xtream)
- `111.onetwo.lat` (HLS con `u`/`p` en query)
- Un m3u8 suelto en `dianshiwang.vip`

Esos ya traen el link (casi) final en el propio M3U.

---

## 6) Resumen en una frase

**El link final de TV = URL del load-balancer `:25461` con `?token=…` (tras el 302 del panel), sirviendo HLS `.m3u8` + segmentos `/hlsr/…`, usando User-Agent tipo ExoPlayer/Lavf y VPN del proveedor.**
