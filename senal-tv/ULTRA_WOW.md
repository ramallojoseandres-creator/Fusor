# SEÑAL TV — Ultra Wow (producción)

## Componentes clave

| Módulo | Ruta |
|--------|------|
| Foco D-pad | `ui/focus/AppFocusableModifier.kt` → `appFocusableModifier()` |
| ExoPlayer | `player/ExoPlayerManager.kt` |
| Red | `network/NetworkMonitor.kt` + `ui/components/ReconnectBanner.kt` |
| Home hub | `ui/home/HomeScreen.kt` |
| Live/Player | `ui/player/PlayerScreen.kt` |

## Comportamiento
- Escala foco **1.06** + borde **#00E5FF**
- ExoPlayer: HW prefer, buffers LIVE/PREVIEW/VOD, reintentos HLS silenciosos
- Si cae `SERVER_IP`: barra superior turquesa **Reconectando…** (backoff exponencial)
- Transiciones `AnimatedContent` Home ↔ Player sin flash negro
