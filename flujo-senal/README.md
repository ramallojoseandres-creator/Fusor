# SEÑAL = fork exacto de FLUJO

Este directorio **no reimplementa** la UI. Toma el APK original de FLUJO (`vendor/flujo-tv-862.apk`), desarma recursos y cambia **solo**:

1. **Nombre** — todo `FLUJO` / `Flujo` → `SEÑAL` / `Señal`
2. **Colores** — naranja `#DC4800` → púrpura SEÑAL `#7C3AED`
3. **Origen de datos** — host `adfereredadasww.ai` → `senalapi-origen.tv` (misma longitud, parche binario en `classes.dex`)

El resto (layouts, animaciones, player IJK, navegación) queda el del APK original.

## Por qué hace falta el bridge

FLUJO habla Magis `/api/v7/...`. SEÑAL habla JWT en `http://185.192.20.245:3000/api/...`. El servicio `bridge/` traduce Magis→SEÑAL.

Apunta DNS o `/etc/hosts`:

```
<IP-del-bridge>  senalapi-origen.tv
```

(El bridge por defecto escucha `:8080`; pon un reverse-proxy en `:80` o cambia el host del APK si usas otro puerto en la URL.)

## Build

```bash
# dependencias: java, apktool, Android build-tools (zipalign/apksigner)
python3 scripts/patch_and_build.py
# → dist/SenalTV.apk
```

```bash
cd bridge && pip install -r requirements.txt
SENAL_BASE_URL=http://185.192.20.245:3000 PORT=8080 python main.py
```

## Nota iJiami

El APK original está protegido con iJiami. Al firmar de nuevo, el shell nativo **puede** rechazar el APK en runtime. Si eso ocurre, hace falta un dump del DEX en dispositivo (BlackDex/Frida) para reconstruir sin packer. El parche de recursos + host sigue siendo la base correcta del fork.
