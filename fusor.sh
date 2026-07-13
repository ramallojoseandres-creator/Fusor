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

# ---------- 3) Clasificar, filtrar adultos, ordenar y generar ----------
LC_ALL=C awk -F'\t' '
BEGIN {
  # Géneros generales, en orden de salida (Deportes SIEMPRE global, sin país)
  ng = split("Deportes|Cultura|Documentales|Series 24/7|Películas|Noticias|Infantil|Música|Variados", GEN, "|")
  KW["Deportes"]     = "DEPORT,SPORT,ESPN,TYC,FUTBOL,FÚTBOL,FúTBOL,GOL TV,GOLTV,BEIN,DAZN,WIN SPORT,NBA,NFL,MLB,UFC,WWE,BOXEO,FORMULA 1,F1,MOTOGP,LIGA"
  KW["Cultura"]      = "CULTUR,ARTE,ENCUENTRO"
  KW["Documentales"] = "DOCU,DISCOVERY,NAT GEO,NATGEO,NATIONAL GEO,HISTORY,ANIMAL PLANET"
  KW["Series 24/7"]  = "24/7,24-7,SERIE"
  KW["Películas"]    = "CINE,PELICULA,PELÍCULA,PELíCULA,MOVIE,FILM,HBO,STAR CHANNEL,PARAMOUNT"
  KW["Noticias"]     = "NOTICIA,NEWS,CNN,TELESUR,GLOBOVISION,GLOBOVISIÓN,NTN24"
  KW["Infantil"]     = "INFANTIL,KIDS,CARTOON,NICK,DISNEY,BABY TV"
  KW["Música"]       = "MUSIC,MÚSICA,MúSICA,MUSICA,MTV,HTV,TELEHIT"

  # Contenido adulto: se elimina de la lista
  ADULTOS = "ADULTO,ADULT,XXX,PORN,+18,18+,EROTIC,ERÓTIC,ERóTIC,SEXO,PLAYBOY,PENTHOUSE,BRAZZERS,HUSTLER,ONLYFANS,VENUS TV,SEXT"

  # Países por NOMBRE (busca en group-title y nombre del canal)
  np = split("VENEZUELA=Venezuela,MEXICO=México,MÉXICO=México,MéXICO=México,COLOMBIA=Colombia,ARGENTINA=Argentina,ESPAÑA=España,ESPAñA=España,ESPANA=España,SPAIN=España,ESTADOS UNIDOS=Estados Unidos,PERU=Perú,PERÚ=Perú,PERú=Perú,CHILE=Chile,ECUADOR=Ecuador,URUGUAY=Uruguay,PARAGUAY=Paraguay,BOLIVIA=Bolivia,PANAMA=Panamá,PANAMÁ=Panamá,PANAMá=Panamá,DOMINICANA=Rep. Dominicana,COSTA RICA=Costa Rica,GUATEMALA=Guatemala,HONDURAS=Honduras,SALVADOR=El Salvador,NICARAGUA=Nicaragua,CUBA=Cuba,PUERTO RICO=Puerto Rico,BRASIL=Brasil,BRAZIL=Brasil,PORTUGAL=Portugal,FRANCIA=Francia,FRANCE=Francia,ITALIA=Italia,ITALY=Italia,ALEMANIA=Alemania,GERMANY=Alemania,CANADA=Canadá,CANADÁ=Canadá,LATINO=Latino,LATAM=Latino", PAISDEF, ",")
  for (i = 1; i <= np; i++) { split(PAISDEF[i], kv, "="); PAIS[kv[1]] = kv[2] }

  # Códigos válidos en tvg-country / tvg-id (lista completa)
  nc = split("VE=Venezuela,MX=México,CO=Colombia,AR=Argentina,ES=España,US=Estados Unidos,PE=Perú,CL=Chile,EC=Ecuador,UY=Uruguay,PY=Paraguay,BO=Bolivia,PA=Panamá,DO=Rep. Dominicana,CR=Costa Rica,GT=Guatemala,HN=Honduras,SV=El Salvador,NI=Nicaragua,CU=Cuba,PR=Puerto Rico,BR=Brasil,PT=Portugal,FR=Francia,IT=Italia,DE=Alemania,CA=Canadá,GB=Reino Unido,UK=Reino Unido,NL=Países Bajos,TR=Turquía", CODDEF, ",")
  for (i = 1; i <= nc; i++) { split(CODDEF[i], kv, "="); COD2[kv[1]] = kv[2] }

  # Códigos permitidos como token suelto en el group-title (se excluyen los
  # ambiguos que son palabras en español: DE, ES, IT, CA, DO, LA)
  nb = split("VE,MX,CO,AR,US,PE,CL,EC,UY,PY,BO,PA,CR,GT,HN,SV,NI,CU,PR,BR,PT,FR,TR,NL,GB,UK", BARE, ",")
  for (i = 1; i <= nb; i++) COD[BARE[i]] = COD2[BARE[i]]
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
function esAdulto(hay,   i, n, a) {
  n = split(ADULTOS, a, ",")
  for (i = 1; i <= n; i++) if (index(hay, a[i]) > 0) return 1
  return 0
}
function pais(ext, gt, hay,   tc, id, t, i, w, nw) {
  # 1) tvg-country explícito
  tc = toupper(getattr(ext, "tvg-country")); gsub(/[ \t]/, "", tc)
  if (tc in COD2) return COD2[tc]
  # 2) prefijo de tvg-id estilo "ve.tves"
  id = getattr(ext, "tvg-id")
  if (match(id, /^[A-Za-z][A-Za-z]\./)) {
    tc = toupper(substr(id, 1, 2))
    if (tc in COD2) return COD2[tc]
  }
  # 3) nombre del país en group-title o nombre del canal
  for (t in PAIS) if (index(hay, t) > 0) return PAIS[t]
  # 4) "USA" como palabra completa (no dentro de otra palabra)
  if (match(hay, /(^|[^A-Z])USA([^A-Z]|$)/)) return "Estados Unidos"
  # 5) código de 2 letras como token, SOLO en el group-title
  nw = split(gt, w, /[^A-Z]+/)
  for (i = 1; i <= nw; i++) if (length(w[i]) == 2 && (w[i] in COD)) return COD[w[i]]
  return ""
}
{
  url = $1; ext = $2
  gt = toupper(getattr(ext, "group-title"))
  c = index(ext, ",")
  nombre = (c > 0) ? substr(ext, c + 1) : url
  # SOLO group-title + nombre del canal (nunca logos ni URLs: evitan falsos "US")
  hay = gt " " toupper(nombre)

  if (esAdulto(hay)) {
    adultos++
    key = "2|Adultos +18"; grupo = "Adultos +18"   # al final de la lista
    print key "\t" toupper(nombre) "\t" setgroup(ext, grupo) "\t" url
    next
  }

  g = genero(hay)
  if (g == "Deportes") {
    key = "0|01|Deportes"; grupo = "Deportes"      # deportes: categoría global única
  } else {
    p = pais(ext, gt, hay)
    if (p == "") {
      gi = ng
      for (i = 1; i <= ng; i++) if (GEN[i] == g) gi = i
      key = sprintf("0|%02d|%s", gi, g); grupo = g
    } else {
      key = "1|" p; grupo = p
    }
  }
  print key "\t" toupper(nombre) "\t" setgroup(ext, grupo) "\t" url
}
END { if (adultos) printf "Canales movidos al grupo Adultos +18: %d\n", adultos > "/dev/stderr" }
' "$TMP/limpios.tsv" | sort -t "$(printf '\t')" -k1,1 -k2,2 | awk -F'\t' '
BEGIN { print "#EXTM3U" }
{ print $3; print $4 }
' > lista_fusionada.m3u

echo "" >&2
echo "✅ Generado: lista_fusionada.m3u ($(grep -c '^#EXTINF' lista_fusionada.m3u) canales)" >&2
