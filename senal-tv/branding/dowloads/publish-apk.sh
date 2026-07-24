#!/usr/bin/env bash
# Publica un APK en el VPS bajo /dowloads (o carpeta local del panel).
# Uso:
#   ./publish-apk.sh /ruta/SenalTV.apk 191 1.8.11 [/ruta/panel/dowloads]
set -euo pipefail

APK="${1:?APK path}"
CODE="${2:?versionCode}"
NAME="${3:?versionName}"
DEST="${4:-./dowloads}"

mkdir -p "$DEST"
cp -f "$APK" "$DEST/SenalTV.apk"
cat > "$DEST/latest.json" <<EOF
{
  "versionCode": ${CODE},
  "versionName": "${NAME}",
  "apk": "SenalTV.apk",
  "changelog": "SEÑAL ${NAME}"
}
EOF
echo "Published → $DEST"
echo "  SenalTV.apk + latest.json (v${NAME} / ${CODE})"
ls -lh "$DEST/SenalTV.apk" "$DEST/latest.json"
