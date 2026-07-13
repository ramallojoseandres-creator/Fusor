#!/bin/bash
# ============================================================
#  FUSOR M3U para Mac — sin instalar nada (bash + awk + curl)
#
#  Uso:
#     bash fusor.sh lista1.m3u lista2.m3u lista3.m3u
#     bash fusor.sh *.m3u
#     bash fusor.sh -n *.m3u     (sin verificar enlaces, más rápido)
#
#  Genera: lista_fusionada.m3u
#
#  Orden de salida:
#    1) Géneros generales (canales sin país detectado):
#       Deportes, Cultura, Documentales, Series 24/7, Películas,
#       Noticias, Infantil, Música, Variados
#    2) Por país (alfabético): "País" (todo menos deportes)
#       y luego "País Deportes"
# ============================================================

set -u
export LC_ALL=C   # evita fallos de awk en Mac con listas que no son UTF-8
TIMEOUT=10        # segundos por enlace al verificar
PARALELO=50       # verificaciones simultáneas
VERIFICAR=1
if [ "${1:-}" = "-n" ]; then VERIFICAR=0; shift; fi

if [ $# -eq 0 ]; then
  echo "Uso: bash fusor.sh [-n] lista1.m3u lista2.m3u ..."
  exit 1
fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ---------- 0) Normalizar codificación (listas viejas en Latin-1 → UTF-8) ----------
ARGS=()
ci=0
for f in "$@"; do
  if LC_ALL=C file "$f" 2>/dev/null | grep -qi "iso-8859"; then
    ci=$((ci+1))
    if iconv -f ISO-8859-1 -t UTF-8 "$f" > "$TMP/utf8_$ci.m3u" 2>/dev/null; then
      echo "(convertida a UTF-8: $f)" >&2
      f="$TMP/utf8_$ci.m3u"
    fi
  fi
  ARGS+=("$f")
done
set -- "${ARGS[@]}"

# ---------- 1) Parsear y deduplicar por URL ----------
awk '
  /^#EXTINF/ { ext = $0; next }
  /^#/       { next }
  /^[ \t]*$/ { next }
  {
    url = $0
    gsub(/^[ \t]+|[ \t\r]+$/, "", url)
    if (url in visto) { dup++; next }
    visto[url] = 1
    if (ext == "") ext = "#EXTINF:-1," url
    gsub(/\r$/, "", ext)
    print url "\t" ext
    ext = ""
    total++
  }
  END { printf "TOTAL %d canales unicos (%d duplicados eliminados)\n", total, dup+0 > "/dev/stderr" }
' "$@" > "$TMP/todos.tsv"

# ---------- 2) Verificar enlaces con curl ----------
if [ "$VERIFICAR" = "1" ]; then
  N=$(wc -l < "$TMP/todos.tsv" | tr -d ' ')
  echo "Verificando $N enlaces (timeout ${TIMEOUT}s, $PARALELO en paralelo)..." >&2
  : > "$TMP/res.txt"
  cut -f1 "$TMP/todos.tsv" | xargs -P "$PARALELO" -n 1 sh -c '
    code=$(curl -s -o /dev/null -m '"$TIMEOUT"' --range 0-0 -w "%{http_code}" "$0" 2>/dev/null)
    case "$code" in
      2*|3*|405|416) printf "V\t%s\n" "$0" ;;
      *)             printf "M\t%s\n" "$0" ;;
    esac
  ' >> "$TMP/res.txt" &
  XPID=$!
  while kill -0 "$XPID" 2>/dev/null; do
    sleep 5
    P=$(wc -l < "$TMP/res.txt" | tr -d " ")
    echo "  ... $P / $N verificados" >&2
  done
  wait "$XPID" 2>/dev/null
  grep '^V' "$TMP/res.txt" | cut -f2 > "$TMP/vivos.txt"
  V=$(wc -l < "$TMP/vivos.txt" | tr -d ' ')
  echo "Vivos: $V — Muertos eliminados: $((N - V))" >&2
  awk -F'\t' 'NR==FNR { ok[$0]=1; next } ($1 in ok)' "$TMP/vivos.txt" "$TMP/todos.tsv" > "$TMP/limpios.tsv"
else
  cp "$TMP/todos.tsv" "$TMP/limpios.tsv"
  echo "(Verificación omitida con -n)" >&2
fi

# ---------- 3) Clasificar por género y país, ordenar y generar ----------
awk -F'\t' '
BEGIN {
  # Géneros en el orden de salida deseado (edítalo a tu gusto)
  ng = split("Deportes|Cultura|Documentales|Series 24/7|Películas|Noticias|Infantil|Música|Variados", GEN, "|")
  KW["Deportes"]     = "DEPORT,SPORT,ESPN,TYC,TNT SPORT,FUTBOL,FÚTBOL,FúTBOL,GOL TV,GOLTV,BEIN,DAZN,WIN SPORT,FOX SPORT,NBA,NFL,MLB,UFC,WWE,BOX,F1,FORMULA"
  KW["Cultura"]      = "CULTUR,ARTE,ENCUENTRO"
  KW["Documentales"] = "DOCU,DISCOVERY,NAT GEO,NATGEO,NATIONAL GEO,HISTORY,ANIMAL PLANET,H2"
  KW["Series 24/7"]  = "24/7,24-7,SERIE"
  KW["Películas"]    = "CINE,PELICULA,PELÍCULA,PELíCULA,MOVIE,FILM,HBO,STAR CHANNEL,PARAMOUNT"
  KW["Noticias"]     = "NOTICIA,NEWS,CNN,TELESUR,GLOBOVISION,GLOBOVISIÓN,NTN24"
  KW["Infantil"]     = "INFANTIL,KIDS,CARTOON,NICK,DISNEY,DISCOVERY KIDS,BABY"
  KW["Música"]       = "MUSIC,MÚSICA,MúSICA,MUSICA,MTV,HTV,TELEHIT"

  # Países: TOKEN=NombreFinal
  np = split("VENEZUELA=Venezuela,MEXICO=México,MÉXICO=México,MéXICO=México,COLOMBIA=Colombia,ARGENTINA=Argentina,ESPAÑA=España,ESPAñA=España,ESPANA=España,SPAIN=España,ESTADOS UNIDOS=Estados Unidos,USA=Estados Unidos,PERU=Perú,PERÚ=Perú,PERú=Perú,CHILE=Chile,ECUADOR=Ecuador,URUGUAY=Uruguay,PARAGUAY=Paraguay,BOLIVIA=Bolivia,PANAMA=Panamá,PANAMÁ=Panamá,PANAMá=Panamá,DOMINICANA=Rep. Dominicana,COSTA RICA=Costa Rica,GUATEMALA=Guatemala,HONDURAS=Honduras,SALVADOR=El Salvador,NICARAGUA=Nicaragua,CUBA=Cuba,PUERTO RICO=Puerto Rico,BRASIL=Brasil,BRAZIL=Brasil,PORTUGAL=Portugal,FRANCIA=Francia,FRANCE=Francia,ITALIA=Italia,ITALY=Italia,ALEMANIA=Alemania,GERMANY=Alemania,CANADA=Canadá,CANADÁ=Canadá", PAISDEF, ",")
  for (i = 1; i <= np; i++) {
    split(PAISDEF[i], kv, "=")
    PAIS[kv[1]] = kv[2]
  }
  # Códigos de 2 letras (tvg-country o " VE |")
  nc = split("VE=Venezuela,MX=México,CO=Colombia,AR=Argentina,ES=España,US=Estados Unidos,PE=Perú,CL=Chile,EC=Ecuador,UY=Uruguay,PY=Paraguay,BO=Bolivia,PA=Panamá,DO=Rep. Dominicana,CR=Costa Rica,GT=Guatemala,HN=Honduras,SV=El Salvador,NI=Nicaragua,CU=Cuba,PR=Puerto Rico,BR=Brasil,PT=Portugal,FR=Francia,IT=Italia,DE=Alemania,CA=Canadá", CODDEF, ",")
  for (i = 1; i <= nc; i++) {
    split(CODDEF[i], kv, "=")
    COD[kv[1]] = kv[2]
  }
}
function getattr(s, key,   p, v, q) {
  p = index(s, key "=\"")
  if (p == 0) return ""
  v = substr(s, p + length(key) + 2)
  q = index(v, "\"")
  return (q > 0) ? substr(v, 1, q - 1) : v
}
function setgroup(ext, g,   p, v, q, c) {
  p = index(ext, "group-title=\"")
  if (p > 0) {
    v = substr(ext, p + 13)
    q = index(v, "\"")
    return substr(ext, 1, p - 1) "group-title=\"" g "\"" substr(v, q + 1)
  }
  c = index(ext, ",")
  if (c > 0) return substr(ext, 1, c - 1) " group-title=\"" g "\"" substr(ext, c)
  return ext " group-title=\"" g "\""
}
function genero(hay,   i, n, kws, k) {
  for (i = 1; i <= ng; i++) {
    if (GEN[i] == "Variados") continue
    n = split(KW[GEN[i]], kws, ",")
    for (k = 1; k <= n; k++) if (index(hay, kws[k]) > 0) return GEN[i]
  }
  return "Variados"
}
function pais(ext, hay,   tc, t, i, w, nw, tok) {
  tc = toupper(getattr(ext, "tvg-country"))
  gsub(/[ \t]/, "", tc)
  if (tc in COD) return COD[tc]
  for (t in PAIS) if (index(hay, t) > 0) return PAIS[t]
  # código de 2 letras aislado en el texto: " VE |", "[MX]", "(AR)"
  nw = split(hay, w, /[^A-Z]+/)
  for (i = 1; i <= nw; i++) { tok = w[i]; if (length(tok) == 2 && (tok in COD)) return COD[tok] }
  return ""
}
{
  url = $1; ext = $2
  hay = toupper(getattr(ext, "group-title") " " ext)
  g = genero(hay)
  p = pais(ext, hay)

  if (p == "") {
    # Sin país: va a los géneros generales
    for (i = 1; i <= ng; i++) if (GEN[i] == g) gi = i
    key = sprintf("0|%02d|%s", gi, g)
    grupo = g
  } else if (g == "Deportes") {
    key = sprintf("1|%s|2", p)          # "País Deportes" después de "País"
    grupo = p " Deportes"
  } else {
    key = sprintf("1|%s|1", p)
    grupo = p
  }
  # nombre del canal para orden alfabético dentro del grupo
  c = index(ext, ",")
  nombre = (c > 0) ? toupper(substr(ext, c + 1)) : url
  print key "\t" nombre "\t" setgroup(ext, grupo) "\t" url
}
' "$TMP/limpios.tsv" | sort -t "$(printf '\t')" -k1,1 -k2,2 | awk -F'\t' '
BEGIN { print "#EXTM3U" }
{ print $3; print $4 }
' > lista_fusionada.m3u

echo "" >&2
echo "✅ Generado: lista_fusionada.m3u ($(grep -c '^#EXTINF' lista_fusionada.m3u) canales)" >&2
