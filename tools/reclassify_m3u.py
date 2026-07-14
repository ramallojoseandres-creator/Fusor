#!/usr/bin/env python3
"""
Reclassify M3U groups — inspired by kamalsoft/m3u-editor Smart Grouping
plus fusor.sh genre / country / adultos rules.

Fixes:
  - Broken EXTINF (unescaped quotes in http-user-agent)
  - Oversized "Variados" bucket: re-assign by channel name keywords
  - Adult content → Adultos +18
  - Leftovers with tvg-id country → country group instead of Variados
  - Drop junk rows (Actualizado…, empty names)

Usage:
  python3 tools/reclassify_m3u.py lista.m3u -o lista_fusionada.m3u
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple


# Order matches fusor.sh general genres
GENRE_ORDER = [
    "Deportes",
    "Cultura",
    "Documentales",
    "Series 24/7",
    "Películas",
    "Noticias",
    "Infantil",
    "Música",
    "Entretenimiento",
    "Estilo de vida",
    "Religión",
    "Variados",
]

# Smart keywords (m3u-editor V2 + Spanish / LATAM / IPTV common names)
# First match wins — order matters (adults checked separately).
GENRE_PATTERNS: List[Tuple[str, List[str]]] = [
    ("Deportes", [
        r"sport", r"deport", r"espn", r"nba", r"nfl", r"mlb", r"nhl",
        r"soccer", r"football", r"futbol", r"fútbol", r"tennis", r"tenis",
        r"golf", r"f1\b", r"formula\s*1", r"racing", r"moto(?:gp|res)?",
        r"bein", r"dazn", r"euro\s*sport", r"fox\s*sports", r"sky\s*sports",
        r"tyc", r"ufc", r"wwe", r"boxeo", r"wrestling", r"olymp", r"liga\b",
        r"gol\s*tv", r"goltv", r"win\s*sport", r"equidia", r"automoto",
        r"canal\s*motor", r"channel\s*fight", r"fishing", r"world\s*fishing",
    ]),
    ("Noticias", [
        r"\bnews\b", r"notici", r"\bcnn\b", r"msnbc", r"fox\s*news",
        r"al\s*jazeera", r"bloomberg", r"\bcnbc\b", r"euronews", r"sky\s*news",
        r"bbc\s*(world|news)", r"france\s*24", r"\bdw\b", r"telesur",
        r"ntn24", r"telemundo", r"univision\s*not", r"c5n", r"tn\b",
        r"globo\s*news", r"weather\b", r"clima\b",
    ]),
    ("Infantil", [
        r"cartoon", r"disney", r"\bnick", r"nickelodeon", r"animation",
        r"\banime\b", r"\bkids\b", r"infantil", r"\bbaby\b", r"boomerang",
        r"cbeebies", r"pbs\s*kids", r"toon", r"junior", r"mattel",
        r"baby\s*tv", r"discovery\s*kids", r"gametoon", r"child",
    ]),
    ("Música", [
        r"\bmusic\b", r"música", r"musica", r"\bmtv\b", r"\bvh1\b",
        r"\bradio\b", r"\bhits\b", r"\bpop\b", r"\brock\b", r"\bdance\b",
        r"telehit", r"cmtv", r"karaoke", r"hip.?hop", r"ourvinyl",
        r"\bjazz\b", r"classical\s*music",
    ]),
    ("Documentales", [
        r"docu", r"\bhistory\b", r"discovery", r"nat\s*geo", r"national\s*geo",
        r"\bplanet\b", r"\bwild\b", r"\bscience\b", r"\banimal\b",
        r"explorer", r"smithsonian", r"crime\b", r"investigation",
        r"id\s*investigation", r"curiosity",
    ]),
    ("Películas", [
        r"\bmovie", r"\bfilm\b", r"\bcinema\b", r"\bcine\b", r"pel[ií]cula",
        r"\bhbo\b", r"cinemax", r"\bstarz\b", r"showtime", r"\bmgm\b",
        r"paramount", r"warner", r"universal\s*(channel|tv|latin|cine)",
        r"sony\s*movie", r"\bfxm\b", r"studio\s*universal", r"tcm\b",
        r"de\s*pelicula", r"space\b", r"isyunik",
    ]),
    ("Series 24/7", [
        r"24/?7", r"24-7", r"\bseries?\b", r"\baxn\b", r"comedy\s*central",
        r"\bnovela\b", r"telenovela", r"sitcom", r"\bserie\b",
        r"fox\s*channel", r"warner\s*channel", r"jump\s*street",
        r"acorn\s*tv", r"\bsitcom\b",
    ]),
    ("Cultura", [
        r"cultur", r"\barte\b", r"encuentro", r"museum", r"theater",
        r"theatre", r"\bópera\b", r"\bopera\b", r"classic(?:s)?\b",
        r"nostalgia", r"heritage",
    ]),
    ("Entretenimiento", [
        r"\bentertain", r"reality", r"variety", r"\be!\b",
        r"\btlc\b", r"\bbravo\b", r"humor", r"\bcomedy\b",
        r"\blate\s*night", r"talk\s*show", r"60\s*days", r"60\s*minutes",
        r"out\s*of\s*10", r"trucking\s*hell", r"haunting", r"pawn\b",
        r"feiticeira", r"caçadora", r"obsessiv",
    ]),
    ("Estilo de vida", [
        r"lifestyle", r"\bfashion\b", r"\bfood\b", r"\bcook", r"\btravel\b",
        r"\bhome\b", r"\bgarden\b", r"\bhgtv\b", r"kitchen", r"gourmet",
        r"turismo", r"viajes", r"crafts?", r"adrenalina?", r"rescue",
    ]),
    ("Religión", [
        r"relig", r"church", r"\bbible\b", r"islam", r"christian",
        r"\bfaith\b", r"gospel", r"iglesia", r"catholic", r"ewtn",
        r"\bgod\b", r"son\s*of\s*god", r"\bjesus\b",
    ]),
]

ADULT_PATTERNS = [
    r"xxx", r"\badult", r"\bporn", r"\bsex\b", r"er[oó]tic", r"\bplayboy\b",
    r"penthouse", r"brazzers", r"hustler", r"onlyfans", r"\+18", r"18\+",
    r"\banal\b", r"blowjob", r"lesbian", r"\bgay\b",
    r"hardcore(?!\s*pawn)", r"fetish",
    r"gangbang", r"cuckold", r"big\s+(ass|tits|dick)", r"brunette", r"blonde",
    r"interracial", r"compilation\s*\|", r"latina\s*\|", r"venus\s*tv",
    r"redlight", r"private\s*gold", r"sex\.es",
]

# Junk channel names to drop entirely
JUNK_NAME = re.compile(
    r"(actualizado|update[d]?|expires?|telegram|whatsapp|"
    r"lista\s*gratis|achoapps|^---|\bhttp\b|\bwww\.|"
    r"^test$|null|undefined)",
    re.I,
)

COUNTRY_FROM_CODE = {
    "VE": "Venezuela", "MX": "México", "CO": "Colombia", "AR": "Argentina",
    "ES": "España", "US": "Estados Unidos", "PE": "Perú", "CL": "Chile",
    "EC": "Ecuador", "UY": "Uruguay", "PY": "Paraguay", "BO": "Bolivia",
    "PA": "Panamá", "DO": "Rep. Dominicana", "CR": "Costa Rica",
    "GT": "Guatemala", "HN": "Honduras", "SV": "El Salvador",
    "NI": "Nicaragua", "CU": "Cuba", "PR": "Puerto Rico", "BR": "Brasil",
    "PT": "Portugal", "FR": "Francia", "IT": "Italia", "DE": "Alemania",
    "CA": "Canadá", "GB": "Reino Unido", "UK": "Reino Unido",
    "NL": "Países Bajos", "TR": "Turquía", "RU": "Rusia", "IN": "India",
    "JP": "Japón", "CN": "China", "AU": "Australia", "PL": "Polonia",
    "SE": "Suecia", "ID": "Indonesia", "IR": "Irán", "TH": "Tailandia",
    "KR": "Corea", "UA": "Ucrania", "GR": "Grecia", "RO": "Rumania",
    "AT": "Austria", "CH": "Suiza", "BE": "Bélgica", "IE": "Irlanda",
    "NZ": "Nueva Zelanda", "PH": "Filipinas", "MY": "Malasia",
    "SG": "Singapur", "HK": "Hong Kong", "TW": "Taiwán", "EG": "Egipto",
    "MA": "Marruecos", "ZA": "Sudáfrica", "NG": "Nigeria", "AE": "EAU",
    "SA": "Arabia Saudita", "IL": "Israel", "PK": "Pakistán",
    "BD": "Bangladés", "MN": "Mongolia", "KZ": "Kazajistán",
}

COUNTRY_NAME_HINTS = [
    (r"venezuela|caracas", "Venezuela"),
    (r"m[eé]xico|mexico", "México"),
    (r"colombia|bogot", "Colombia"),
    (r"argentina|buenos\s*aires", "Argentina"),
    (r"espa[nñ]a|spain|madrid", "España"),
    (r"estados\s*unidos|\busa\b|\bu\.s\.a", "Estados Unidos"),
    (r"per[uú]", "Perú"),
    (r"\bchile\b|santiago", "Chile"),
    (r"ecuador|quito", "Ecuador"),
    (r"brasil|brazil", "Brasil"),
    (r"latino|latam|hispanic", "Latino"),
]


@dataclass
class Entry:
    name: str = ""
    url: str = ""
    group: str = "Variados"
    logo: str = ""
    tvg_id: str = ""
    tvg_chno: str = ""
    duration: str = "-1"
    user_agent: str = ""
    extras: Dict[str, str] = field(default_factory=dict)

    def to_m3u(self) -> str:
        attrs = [f"#EXTINF:{self.duration or '-1'}"]
        if self.tvg_id:
            attrs.append(f'tvg-id="{_q(self.tvg_id)}"')
        if self.logo:
            attrs.append(f'tvg-logo="{_q(self.logo)}"')
        if self.user_agent:
            attrs.append(f'http-user-agent="{_q(self.user_agent)}"')
        attrs.append(f'group-title="{_q(self.group)}"')
        return " ".join(attrs) + f",{self.name}\n{self.url}"


def _q(s: str) -> str:
    return s.replace('"', "'")


def _attr(ext: str, key: str) -> str:
    m = re.search(rf'{re.escape(key)}="([^"]*)"', ext)
    return m.group(1).strip() if m else ""


def _parse_name(ext: str) -> str:
    # Prefer ",NAME" after the last well-formed group-title="…"
    m = re.search(r'group-title="[^"]*",(.*)$', ext)
    if m:
        return m.group(1).strip()
    # Fallback: text after the last comma
    if "," in ext:
        return ext.rsplit(",", 1)[-1].strip()
    return ""


def _repair_extinf(ext: str) -> str:
    """Recover attributes when http-user-agent quotes are broken."""
    # If group-title already parses, leave as-is
    if _attr(ext, "group-title"):
        return ext
    # Try to salvage group-title / name from trailing garbage
    m = re.search(r'group-title="([^"]*)",(.*)$', ext)
    if m:
        return ext
    return ext


def parse_m3u(path: Path) -> List[Entry]:
    entries: List[Entry] = []
    ext: Optional[str] = None
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXTM3U"):
            continue
        if line.startswith("#EXTINF"):
            ext = _repair_extinf(line)
            continue
        if line.startswith("#"):
            continue
        if not (line.startswith("http://") or line.startswith("https://") or "://" in line):
            continue
        if not ext:
            continue

        ua = _attr(ext, "http-user-agent")
        # Broken UA often leaves Chrome/... as the "name" — recover
        name = _parse_name(ext)
        if "group-title=" in name or name.startswith("like Gecko"):
            m = re.search(r'group-title="[^"]*",(.*)$', ext)
            name = m.group(1).strip() if m else name

        e = Entry(
            name=name or "Unknown",
            url=line,
            group=_attr(ext, "group-title") or "Variados",
            logo=_attr(ext, "tvg-logo"),
            tvg_id=_attr(ext, "tvg-id"),
            tvg_chno=_attr(ext, "tvg-chno"),
            user_agent=ua,
        )
        dur = re.search(r"#EXTINF:([-0-9]+)", ext)
        if dur:
            e.duration = dur.group(1)
        entries.append(e)
        ext = None
    return entries


def is_adult(hay: str) -> bool:
    return any(re.search(p, hay, re.I) for p in ADULT_PATTERNS)


def detect_genre(hay: str) -> Optional[str]:
    for genre, patterns in GENRE_PATTERNS:
        for p in patterns:
            if re.search(p, hay, re.I):
                return genre
    return None


def country_from_tvg_id(tvg_id: str) -> str:
    if not tvg_id:
        return ""
    # ve.tves / mx.azteca
    m = re.match(r"^([A-Za-z]{2})\.", tvg_id)
    if m:
        code = m.group(1).upper()
        if code in COUNTRY_FROM_CODE:
            return COUNTRY_FROM_CODE[code]
    # HardKnocks.ca@SD / Canal.es@HD
    m = re.search(r"\.([A-Za-z]{2})@", tvg_id)
    if m:
        code = m.group(1).upper()
        if code in COUNTRY_FROM_CODE:
            return COUNTRY_FROM_CODE[code]
    return ""


def country_from_name(hay: str) -> str:
    for pat, country in COUNTRY_NAME_HINTS:
        if re.search(pat, hay, re.I):
            return country
    # trailing " | US" / " | UK"
    m = re.search(r"\|\s*([A-Z]{2})\s*$", hay)
    if m and m.group(1) in COUNTRY_FROM_CODE:
        return COUNTRY_FROM_CODE[m.group(1)]
    return ""


def classify(entry: Entry) -> str:
    hay = f"{entry.group} {entry.name} {entry.tvg_id}"
    if is_adult(hay):
        return "Adultos +18"

    genre = detect_genre(hay)
    # Sports always global
    if genre == "Deportes":
        return "Deportes"

    if genre and genre != "Variados":
        return genre

    # No strong genre → try country so we don't dump everything in Variados
    country = country_from_tvg_id(entry.tvg_id) or country_from_name(hay)
    if country:
        return country

    return entry.group if entry.group and entry.group not in ("", "Undefined") else "Variados"


def sort_key(entry: Entry) -> Tuple:
    g = entry.group
    if g == "Deportes":
        return (0, "01", g, entry.name.upper())
    if g == "Adultos +18":
        return (2, g, entry.name.upper())
    if g in GENRE_ORDER:
        return (0, f"{GENRE_ORDER.index(g):02d}", g, entry.name.upper())
    # Country buckets
    return (1, g, entry.name.upper())


def reclassify(entries: List[Entry]) -> Tuple[List[Entry], Counter, int]:
    moved = Counter()
    out: List[Entry] = []
    dropped = 0
    seen_urls = set()

    for e in entries:
        if JUNK_NAME.search(e.name.strip()) or len(e.name.strip()) < 2:
            dropped += 1
            continue
        if e.url in seen_urls:
            dropped += 1
            continue
        seen_urls.add(e.url)

        old = e.group
        new = classify(e)
        if old != new:
            moved[f"{old} → {new}"] += 1
        e.group = new
        out.append(e)

    out.sort(key=sort_key)
    return out, moved, dropped


def write_m3u(path: Path, entries: List[Entry]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as f:
        f.write("#EXTM3U\n")
        f.write("# Reclassified with tools/reclassify_m3u.py (m3u-editor smart groups + fusor)\n")
        for e in entries:
            f.write(e.to_m3u())
            f.write("\n")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("-o", "--output", type=Path, required=True)
    args = ap.parse_args()

    entries = parse_m3u(args.input)
    print(f"Parsed {len(entries)} entries", file=sys.stderr)

    before = Counter(e.group for e in entries)
    out, moved, dropped = reclassify(entries)
    after = Counter(e.group for e in out)

    print(f"Dropped junk/dupes: {dropped}", file=sys.stderr)
    print("\nBefore (top):", file=sys.stderr)
    for g, n in before.most_common(15):
        print(f"  {n:5d}  {g}", file=sys.stderr)
    print("\nAfter (top):", file=sys.stderr)
    for g, n in after.most_common(25):
        print(f"  {n:5d}  {g}", file=sys.stderr)
    print(f"\nMoves: {sum(moved.values())}", file=sys.stderr)
    for k, n in moved.most_common(30):
        print(f"  {n:5d}  {k}", file=sys.stderr)

    write_m3u(args.output, out)
    print(f"\nWrote {len(out)} → {args.output}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
