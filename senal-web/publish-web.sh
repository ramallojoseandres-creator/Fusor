#!/usr/bin/env bash
# Build y copia SEÑAL Web al public del panel (VPS).
#
# Uso:
#   ./publish-web.sh                          # → ./public/web (local)
#   ./publish-web.sh /ruta/del/panel/public/web
#   ./publish-web.sh user@185.192.20.245:/ruta/del/panel/public/web
#
# En el Express del panel (una vez):
#   app.use('/web', express.static(path.join(__dirname, 'public/web')))
#   app.get(/^\/web(\/.*)?$/, (_req, res) => {
#     res.sendFile(path.join(__dirname, 'public/web', 'index.html'))
#   })
#
# URL: http://185.192.20.245:3000/web/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
DEST="${1:-$ROOT/public/web}"
BASE="${VITE_BASE:-/web/}"

cd "$ROOT"
echo "Building SEÑAL Web (base=${BASE})…"
VITE_BASE="$BASE" npm run build

if [[ "$DEST" == *:* && "$DEST" != /* ]]; then
  # remote scp target: user@host:/path
  echo "Uploading → $DEST"
  ssh "${DEST%%:*}" "mkdir -p '${DEST#*:}'"
  scp -r dist/. "$DEST/"
else
  mkdir -p "$DEST"
  rm -rf "${DEST:?}/"*
  cp -a dist/. "$DEST/"
  echo "Published → $DEST"
fi

echo "Listo. Abre: http://185.192.20.245:3000/web/"
