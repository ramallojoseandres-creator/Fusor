# SEÑAL para iPhone (IPA)

Cliente **iOS 16+** con la misma arquitectura que Android:

| Capa | Rol |
|------|-----|
| Servidor SEÑAL | Solo login JWT (`POST /api/auth/login`) |
| Bundle de la app | `lista_fusionada.m3u` embebida (~9.8k canales) |
| iPhone | AVPlayer abre el stream directo |

## Importante sobre el IPA

Apple **exige firma** para instalar en un iPhone real. Este entorno (Linux) **no puede** generar un `.ipa` firmado e instalable sin:

1. Un Mac con Xcode, **y**
2. Tu Apple ID (gratis, con recarga cada 7 días) **o** cuenta Developer de pago ($99/año) / TestFlight.

Aquí entregamos el **código fuente + workflow** listo para firmar en tu máquina o en GitHub Actions con secretos.

## Instalar en tu iPhone (recomendado: Xcode)

1. En un Mac: instala Xcode 15+.
2. En la carpeta `senal-ios/`:

```bash
brew install xcodegen
xcodegen generate
open SenalTV.xcodeproj
```

3. En Xcode: selecciona el target **SenalTV** → **Signing & Capabilities** → marca *Automatically manage signing* → elige tu **Team** (Apple ID).
4. Conecta el iPhone por cable, confía en el ordenador.
5. Pulsa Run ▶️. La primera vez: en el iPhone → Ajustes → General → VPN y gestión de dispositivos → confiar en tu certificado.

Así queda instalada como app nativa (equivalente a un IPA firmado con tu cuenta).

## Generar IPA (Ad Hoc / Development)

```bash
cd senal-ios
xcodegen generate
xcodebuild -scheme SenalTV -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/SenalTV.xcarchive archive
# Export con un ExportOptions.plist firmado por tu equipo
```

GitHub Actions (`senal-ios.yml`) arma el proyecto en macOS; la exportación firmada solo funciona si configuras los secretos `IOS_CERTIFICATE`, `IOS_PROVISIONING_PROFILE`, `IOS_TEAM_ID`.

## Alternativas sin Mac propio

- **GitHub Codespaces / MacStadium / Mac en la nube** + los pasos de Xcode.
- **AltStore / Sideloadly**: firman el IPA con tu Apple ID desde un PC (sigue haciendo falta un IPA firmable o el `.app` empaquetado).

## Actualizar canales

```bash
cp lista_fusionada.m3u senal-ios/SenalTV/Resources/
# opcional gzip
gzip -c -9 lista_fusionada.m3u > senal-ios/SenalTV/Resources/lista_fusionada.m3u.gz
```
