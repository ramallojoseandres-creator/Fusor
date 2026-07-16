# SEÑAL para iPhone + LiveContainer

Misma lógica que Android: **login en servidor**; la lista se descarga **una vez** (`/playlist.m3u`) y se guarda en el iPhone (bundle solo de respaldo).

## Sí: con LiveContainer puedes usarlo

LiveContainer **no necesita** un IPA firmado por App Store. Tú importas el IPA y LiveContainer lo firma en el dispositivo con el certificado de AltStore/SideStore (modo JIT-Less).

### 1) Requisitos en el iPhone

- LiveContainer instalado ([guía oficial](https://livecontainer.github.io/docs/installation))
- AltStore 2.2.1+ o SideStore 0.6.2+
- En LiveContainer → **Settings** → **Import Certificate from AltStore/SideStore**
- **JIT-Less Mode Diagnose** → Test Passed

### 2) Bajarte el IPA

1. GitHub → **Actions** → **Build SEÑAL iOS IPA (LiveContainer)** → Run workflow  
   **o** descarga el artefacto `SenalTV-LiveContainer-ipa` del último run
2. También puede salir en **Releases** como `SenalTV-LiveContainer.ipa`
3. Pasa el `.ipa` al iPhone (AirDrop, Files, iCloud, Safari…)

### 3) Instalar dentro de LiveContainer

1. Abre **LiveContainer**
2. Pestaña **My Apps** → botón **+** (arriba derecha)
3. Elige `SenalTV-LiveContainer.ipa`
4. Selecciónala para el próximo lanzamiento → ábrela

También puedes compartir el IPA desde Archivos → **Abrir con LiveContainer** (a veces está bajo “Más”).

URL de instalación (si tienes el IPA en un link directo):

```text
livecontainer://install?url=https://…/SenalTV-LiveContainer.ipa
```

### Notas

- No consumes un “slot” extra de Apple ID por cada app: viven dentro de LiveContainer.
- Si el certificado de AltStore/SideStore se renueva, vuelve a importarlo en LiveContainer.
- Login SEÑAL sigue haciendo falta (usuarios); el catálogo no sale del servidor.

## Build local (Mac) → IPA LiveContainer

```bash
cd senal-ios
brew install xcodegen
xcodegen generate
xcodebuild -scheme SenalTV -configuration Release -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO build
mkdir -p dist/Payload
cp -R build/DerivedData/Build/Products/Release-iphoneos/SenalTV.app dist/Payload/
(cd dist && zip -qry SenalTV-LiveContainer.ipa Payload)
```

## Actualizar canales

Filtrar enlaces muertos (health-check estilo [kamalsoft/m3u-editor](https://github.com/kamalsoft/m3u-editor)) y embeber:

```bash
python3 tools/filter_m3u.py lista_fusionada.m3u -o lista_fusionada.m3u
cp lista_fusionada.m3u senal-ios/SenalTV/Catalog/playlist.m3u
cp lista_fusionada.m3u senal-ios/SenalTV/Resources/lista_fusionada.m3u
gzip -c -9 lista_fusionada.m3u > senal-ios/SenalTV/Catalog/playlist.dat
gzip -c -9 lista_fusionada.m3u > senal-ios/SenalTV/Resources/lista_fusionada.m3u.gz
```
