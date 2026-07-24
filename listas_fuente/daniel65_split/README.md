# VOD Daniel65 (películas / series)

Listas separadas del panel PlanetTV / Daniel65:

| Archivo | Uso en app |
|---|---|
| `daniel65_peliculas.m3u` | Sección **PELÍCULAS** |
| `daniel65_series.m3u` | Sección **SERIES** |

Actualizar assets (plain `.m3u` — aapt2 rompe los `.gz`):

```bash
cp listas_fuente/daniel65_split/daniel65_peliculas.m3u \
  senal-tv/app/src/main/assets/vod/daniel65_peliculas.m3u
cp listas_fuente/daniel65_split/daniel65_series.m3u \
  senal-tv/app/src/main/assets/vod/daniel65_series.m3u
```
