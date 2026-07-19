#!/usr/bin/env bash
# Genera ResQRadar-VE-LiveContainer.ipa (Mac + Flutter + Xcode).
# LiveContainer re-firma el IPA; no hace falta Apple Developer de pago.
set -euo pipefail
cd "$(dirname "$0")/../flutter_app"
flutter pub get
flutter build ios --release --no-codesign
APP="build/ios/iphoneos/Runner.app"
test -d "$APP"
rm -rf dist/Payload
mkdir -p dist/Payload
cp -R "$APP" dist/Payload/Runner.app
(
  cd dist
  rm -f ResQRadar-VE-LiveContainer.ipa
  zip -qry ResQRadar-VE-LiveContainer.ipa Payload
)
ls -lh dist/ResQRadar-VE-LiveContainer.ipa
echo "Listo → dist/ResQRadar-VE-LiveContainer.ipa"
echo "LiveContainer: My Apps → + → este IPA"
