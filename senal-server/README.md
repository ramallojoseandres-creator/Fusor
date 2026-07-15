# SEÑAL Server PRO (Windows VPS)

Compatible con el paquete **Senal-Server-M3U-Simple**:

1. `INSTALAR-WINDOWS.bat` → instala Node (si falta), crea `.env`, `npm install`, abre firewall :3000  
2. `INICIAR-SENAL.bat` → arranca el panel

## Panel

Abre `http://IP:3000/`

- **Usuarios** — crear / bloquear / vencer / límite de dispositivos / liberar  
- **Banner** — mensajes del home de la APK (`GET /api/banner`)  
- **Logs** — logins, fallos, cambios admin, imports  
- **Importar M3U** — actualiza catálogo del servidor (`data/db.json`)

## Migrar desde tu servidor actual

1. Detén el servidor viejo (cierra la ventana de `INICIAR-SENAL`).  
2. Copia esta carpeta `senal-server` al VPS (o reemplaza archivos).  
3. Conserva tu `data/db.json` y tu `.env`.  
4. Ejecuta `INSTALAR-WINDOWS.bat` (instala `dotenv` nuevo).  
5. Ejecuta `INICIAR-SENAL.bat`.

El servidor migra solo el esquema (añade `banners` y `logs` si faltan) sin borrar usuarios/dispositivos.

## Variables `.env`

Ver `.env.example`:

```
PORT=3000
PUBLIC_BASE_URL=http://185.192.20.245:3000
JWT_SECRET=...
MASTER_USERNAME=admin
MASTER_PASSWORD=...
PLAYBACK_MODE=proxy
```

## Seguridad

Si compartiste el ZIP con `.env` real, **cambia** `MASTER_PASSWORD` y `JWT_SECRET`.
