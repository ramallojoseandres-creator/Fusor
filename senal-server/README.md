# SEÑAL Server 2.0 — Panel IPTV

Panel de control pro para gestionar:

- Usuarios (activar / bloquear / eliminar)
- Límite de dispositivos por cuenta
- Fecha de vencimiento
- Mensajes del **banner de noticias** del home de la TV
- Importación M3U (conteo / archivo en `data/`)

## Despliegue en el VPS (`185.192.20.245:3000`)

```bash
# En el servidor (reemplaza el panel actual)
cd /opt  # o la carpeta donde corra hoy el servicio
# detén el proceso anterior (pm2/systemd)
pm2 stop senal-server || true

# copia esta carpeta senal-server/
cd senal-server
npm install --omit=dev
export SENAL_MASTER_USER=admin
export SENAL_MASTER_PASS='TU_CLAVE_SEGURA'
export SENAL_JWT_SECRET='cadena-larga-secreta'
export PORT=3000
pm2 start server.js --name senal-server
pm2 save
```

Abre `http://TU_IP:3000/` → login master → pestaña **Banner / noticias**.

## API relevante para la APK

| Método | Ruta | Uso |
|--------|------|-----|
| GET | `/api/banner` | Avisos activos del home |
| GET/POST/PUT/DELETE | `/api/admin/banners` | CRUD avisos (admin) |
| POST | `/api/admin/users` | Crear usuario + `connectionLimit` + `expiresAt` |
| PATCH | `/api/admin/users/:id` | Editar estado / devices / expiry |
| DELETE | `/api/admin/users/:id/devices` | Liberar dispositivos |

La APK 1.9+ lee `GET /api/banner` al abrir el home.
