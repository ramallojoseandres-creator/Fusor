# SEÑAL Server IPTV PRO 3.1 (Windows VPS)

Panel reseller: usuarios, bouquets, **catálogo con renombrar/borrar/ordenar**, export M3U,
y **catálogo rápido JSON.gz** para la APK (sin embutir la lista en el APK).

## Instalación

1. `INSTALAR-WINDOWS.bat` → Node, `.env`, `npm install`, firewall :3000  
2. `INICIAR-SENAL.bat` → panel en `http://IP:3000/`

Si `data/db.json` está vacío, el servidor hace **seed** automático desde
`../lista_fusionada.m3u` (repo) o `data/lista_importada.m3u`.

## Panel PRO

| Pestaña | Función |
|---------|---------|
| **Dashboard** | Stats, URL M3U, etag del catálogo rápido |
| **Catálogo** | Drag&drop orden · renombrar/borrar categorías · editar/borrar canales · crear |
| **Paquetes** | Bouquets por categorías |
| **Usuarios** | Crear, bloquear, vencer, dispositivos, URL M3U |
| **Banner** | Avisos del home APK |
| **Logs** | Logins, imports, ediciones |
| **Importar M3U** | Carga masiva; luego organiza en Catálogo |

## APK — catálogo fuera del APK (rápido)

```
GET /api/catalog/meta          → { etag, total, generatedAt }
GET /api/catalog/fast          → JSON gzip preindexado (categorías + canales)
Header If-None-Match: <etag>   → 304 si no cambió (0 bytes)
```

La APK cachea el JSON en disco y muestra **categorías al instante** al abrir EN VIVO,
sin esperar a que el canal reproduzca.

## URL M3U (apps IPTV externas)

```
http://TU-IP:3000/get.php?username=USUARIO&password=CLAVE&type=m3u_plus
```

## API admin (catálogo)

- `PUT /api/admin/catalog/categories/order` — orden categorías  
- `PUT /api/admin/catalog/channels/order` — orden canales  
- `PATCH /api/admin/categories/:name` — renombrar  
- `DELETE /api/admin/categories/:name?mode=dissolve|purge` — borrar  
- `POST /api/admin/categories` — crear vacía  
- `POST /api/admin/channels` — crear canal  
- `PATCH /api/admin/channels/:id` — renombrar / mover / ocultar  
- `DELETE /api/admin/channels/:id` — borrar  
- `GET /api/admin/export.m3u` — descarga M3U ordenado  

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
