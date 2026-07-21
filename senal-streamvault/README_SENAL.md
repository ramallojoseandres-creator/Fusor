# SEÑAL (StreamVault UI + login de panel)

Basada en el APK original StreamVault-IPTV.

## Cambios SEÑAL
- Inicio de sesión (usuario/contraseña) contra `POST /api/auth/login`
- Tras login, carga automática de `GET /playlist.m3u` con `Authorization: Bearer <jwt>`
- Sin Plugins en la navegación
- Sin Downloads (nav + botones)
- Sin carga manual de M3U por el usuario en el arranque

Package: `com.senal.streamvault` · versión `2.0.0`
