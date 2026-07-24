# SEÑAL para iPhone + LiveContainer

Misma lógica que el APK **1.8.5**: login + `GET /api/catalog` una vez → caché en disco.

## LiveContainer

1. Instala LiveContainer + importa certificado AltStore/SideStore (JIT-Less)
2. Descarga el artefacto **SenalTV-LiveContainer-ipa** de Actions
3. LiveContainer → My Apps → **+** → `SenalTV-LiveContainer.ipa`

```text
livecontainer://install?url=https://…/SenalTV-LiveContainer.ipa
```

## Rutas del panel

Base: `http://185.192.20.245:3000/`

| Uso | Método | Ruta |
|-----|--------|------|
| Login | POST | `/api/auth/login` |
| Catálogo | GET | `/api/catalog` |

Headers: `Authorization: Bearer <token>` + `X-Device-Id` (vía RawHTTP / Network.framework para LiveContainer).

## Build local (Mac)

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
