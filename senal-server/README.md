# SEÑAL Server IPTV 3.0 (Windows VPS)

Panel reseller tipo servicios IPTV: usuarios, bouquets, catálogo con **orden editable**, export M3U y URL clásica `/get.php`.

## Instalación

1. `INSTALAR-WINDOWS.bat` → Node, `.env`, `npm install`, firewall :3000  
2. `INICIAR-SENAL.bat` → arranca el panel en `http://IP:3000/`

Ver `COMO-INSTALAR.txt` para migrar desde v2.1 conservando `data/db.json`.

## Panel

| Pestaña | Función |
|---------|---------|
| **Dashboard** | Stats, plantilla URL M3U, exportar playlist ordenada |
| **Catálogo** | Arrastrar orden de categorías y canales; ocultar canales |
| **Paquetes** | Bouquets: subset de categorías por usuario |
| **Usuarios** | Crear, bloquear, vencer, límite dispositivos, paquete, URL M3U |
| **Banner** | Avisos del home APK (`GET /api/banner`) |
| **Logs** | Logins, imports, cambios de orden |
| **Importar M3U** | Carga catálogo; luego reordena en Catálogo |

## URL M3U (clientes / apps IPTV)

```
http://TU-IP:3000/get.php?username=USUARIO&password=CLAVE&type=m3u_plus
```

El M3U respeta `categoryOrder` y `sort` definidos en el panel.

## API admin (resumen)

- `GET/PUT /api/admin/catalog/categories/order` — orden categorías  
- `PUT /api/admin/catalog/channels/order` — orden canales en un grupo  
- `GET /api/admin/export.m3u` — descarga M3U ordenado  
- `GET/POST/PATCH/DELETE /api/admin/bouquets` — paquetes  

## Variables `.env`

```
PORT=3000
PUBLIC_BASE_URL=http://185.192.20.245:3000
JWT_SECRET=...
MASTER_USERNAME=admin
MASTER_PASSWORD=...
```

## Seguridad

Rota `MASTER_PASSWORD` y `JWT_SECRET` si compartiste el ZIP o `.env`.
