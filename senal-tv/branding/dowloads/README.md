# Actualizaciones SEÑAL desde el VPS (`/dowloads`)

La app mira aquí:

```
http://185.192.20.245:3000/dowloads/latest.json
http://185.192.20.245:3000/dowloads/SenalTV.apk
```

(También acepta `/downloads/` si renombras la carpeta.)

## Pasos en el servidor

### 1. Carpeta pública
En el panel (Express / Node), sirve la carpeta estática:

```js
// ejemplo Express
const path = require('path')
const express = require('express')
const app = express()

// Misma ruta que usas hoy:
app.use('/dowloads', express.static(path.join(__dirname, 'dowloads'), {
  setHeaders(res) {
    res.setHeader('Cache-Control', 'no-cache')
  }
}))
```

Crea la carpeta si no existe:

```bash
mkdir -p /ruta/del/panel/dowloads
```

### 2. Subir el APK
Copia el APK con nombre fijo (recomendado):

```bash
cp SenalTV.apk /ruta/del/panel/dowloads/SenalTV.apk
```

### 3. Publicar `latest.json`
Edita `/ruta/del/panel/dowloads/latest.json`:

```json
{
  "versionCode": 191,
  "versionName": "1.8.11",
  "apk": "SenalTV.apk",
  "changelog": "Icono + OTA"
}
```

**Importante:** `versionCode` debe ser **mayor** que el de la app instalada
(ver `senal-tv/app/build.gradle.kts` → `versionCode`).

### 4. Comprobar en el navegador
- http://185.192.20.245:3000/dowloads/latest.json → JSON visible  
- http://185.192.20.245:3000/dowloads/SenalTV.apk → descarga el APK  

### 5. En la TV / BlueStacks
1. Abre SEÑAL → en Home aparece **Actualizar vX.Y** si hay versión nueva.  
2. O ve a **Ajustes → App**.  
3. La primera vez Android pedirá **permitir instalar apps desconocidas** para SEÑAL.  
4. Confirma la instalación (sustituye la app; **no hace falta borrarla**).

## Script rápido (opcional)

Desde tu PC, tras generar el APK:

```bash
scp dist/SenalTV.apk root@185.192.20.245:/ruta/del/panel/dowloads/SenalTV.apk
ssh root@185.192.20.245 'cat > /ruta/del/panel/dowloads/latest.json' <<EOF
{
  "versionCode": 191,
  "versionName": "1.8.11",
  "apk": "SenalTV.apk",
  "changelog": "Nueva build"
}
EOF
```

## Notas
- El APK debe firmarse con la **misma keystore** que la app instalada (debug → debug).  
- Builds `debug` usan `applicationId` `com.senal.tv.debug`; publica el mismo flavor.  
- Si `latest.json` no existe, el botón de Home no aparece; en Ajustes verás el aviso.
