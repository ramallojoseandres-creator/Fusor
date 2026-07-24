# VOD Daniel65 (películas / series)

Listas separadas del panel PlanetTV / Daniel65:

| Archivo | Uso en app |
|---|---|
| `daniel65_peliculas.m3u` | Sección **PELÍCULAS** |
| `daniel65_series.m3u` | Sección **SERIES** |

En el APK van comprimidas:

- `senal-tv/app/src/main/assets/vod/daniel65_peliculas.m3u.gz`
- `senal-tv/app/src/main/assets/vod/daniel65_series.m3u.gz`

La UI muestra **categorías a la izquierda** (`group-title`) y títulos en lista a la derecha (sin mural/banner de cine).

Actualizar assets:

```bash
gzip -c -9 listas_fuente/daniel65_split/daniel65_peliculas.m3u \
  > senal-tv/app/src/main/assets/vod/daniel65_peliculas.m3u.gz
gzip -c -9 listas_fuente/daniel65_split/daniel65_series.m3u \
  > senal-tv/app/src/main/assets/vod/daniel65_series.m3u.gz
```
