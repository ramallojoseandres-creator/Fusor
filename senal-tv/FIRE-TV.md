# SEÑAL 1.2 — Fire TV + Android TV

APK única para **Amazon Fire TV** (Stick, Stick 4K, 4K Max, Cube) y **Android TV** genérico.

## Requisitos del dispositivo

- Android / Fire OS con **Leanback** (interfaz TV)
- Control remoto (D-pad)
- ARM (`armeabi-v7a` o `arm64-v8a`)

## Controles

| Tecla | Acción |
|--------|--------|
| Flechas | Navegar |
| SELECT / OK | Abrir sección / sintonizar canal / mostrar-ocultar guía |
| Mantener SELECT | Añadir o quitar favorito |
| Menú | Abrir o cerrar guía (en reproducción) |
| Atrás | Cerrar guía o volver |

## Servidor

Misma API: `http://185.192.20.245:3000/`

## Compilar

```bash
cd senal-tv
./gradlew assembleTvRelease
```

Salida: `app/build/outputs/apk/tv/release/app-tv-release.apk`
