# Demo M3UAndroid (prueba — NO es SEÑAL oficial)

Versión de **prueba** usando el reproductor open-source [M3UAndroid](https://github.com/oxyroid/M3UAndroid) + una **lista demo** de videos públicos.

- **No** usa la playlist oficial SEÑAL
- **No** es el APK SEÑAL (`com.senal.tv`)
- Solo para probar IPTV / M3U en Fire TV, tablet o teléfono

## Archivos

| Archivo | Uso |
|---------|-----|
| `M3UAndroid-1.15.0-arm64.apk` | Fire Stick / TV ARM64 |
| `M3UAndroid-1.15.0-universal.apk` | Más dispositivos |
| `playlist-demo.m3u` | Lista demo (Big Buck Bunny, etc.) |

## Cómo probar

1. Instala el APK (orígenes desconocidos en Fire TV)
2. Abre **M3UAndroid**
3. Añade playlist con la URL del `.m3u` (raw de GitHub) o copia el archivo a la USB/Descargas
4. Reproduce un canal del grupo **Demo**

### URL de la lista demo (cuando esté en el repo)

```
https://raw.githubusercontent.com/ramallojoseandres-creator/Fusor/main/demo/m3uandroid/playlist-demo.m3u
```

(Si la rama aún es de PR, usa la URL raw de esa rama.)

## Licencia

M3UAndroid: GPL-3.0 — © oxyroid.  
Lista demo: streams de muestra de Google (gtv-videos-bucket).
