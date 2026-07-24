export type Channel = {
  id: string
  number: number
  name: string
  logo?: string
  group: string
  tvgId: string
  url: string
}

const PREFERRED_ORDER = [
  'Copa Mundial',
  'MLB PASS',
  'NBA PASS',
  'NFL PASS',
  'Eventos PPV',
  'Full HD',
  'HD+(265)',
  'Deportes',
  'Cine y Series',
  'Cultura',
  'Infantil',
  'Noticias',
  'Religioso',
  'Música',
  'Premium Español',
  'Canales 24/7',
  'Cinema Channels',
  'Argentina',
  'Bolivia',
  'Brasil',
  'Canadá',
  'República Dominicana',
  'Chile',
  'Colombia',
  'Centroamérica',
  'Costa Rica',
  'España',
  'Ecuador',
  'El Salvador',
  'Honduras',
  'Panamá',
  'Paraguay',
  'México',
  'Perú',
  'Puerto Rico',
  'Uruguay',
  'US Channels',
  'Venezuela',
  'Italia',
  'Adultos',
] as const

const ALIASES: Record<string, string> = {
  'cine premium': 'Premium Español',
  'premium espanol': 'Premium Español',
  'premium español': 'Premium Español',
  'cinema channel': 'Cinema Channels',
  'cinema channels': 'Cinema Channels',
  'republica dominicana': 'República Dominicana',
  'república dominicana': 'República Dominicana',
  'rep. dominicana': 'República Dominicana',
  canada: 'Canadá',
  canadá: 'Canadá',
  mexico: 'México',
  méxico: 'México',
  peru: 'Perú',
  perú: 'Perú',
  panama: 'Panamá',
  panamá: 'Panamá',
  musica: 'Música',
  música: 'Música',
  espana: 'España',
  españa: 'España',
  centroamerica: 'Centroamérica',
  centroamérica: 'Centroamérica',
  'hd+(265)': 'HD+(265)',
  'hd+ (265)': 'HD+(265)',
  'full hd': 'Full HD',
  'copa mundial': 'Copa Mundial',
  deportes: 'Deportes',
  sports: 'Deportes',
  adulto: 'Adultos',
  adultos: 'Adultos',
  'adultos +18': 'Adultos',
  'adultos+18': 'Adultos',
  adult: 'Adultos',
  xxx: 'Adultos',
  entretenimiento: 'Cine y Series',
  peliculas: 'Cine y Series',
  películas: 'Cine y Series',
  'estados unidos': 'US Channels',
  usa: 'US Channels',
  us: 'US Channels',
}

function normalize(s: string) {
  return s
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .trim()
}

const preferredIndex = new Map(PREFERRED_ORDER.map((n, i) => [normalize(n), i]))

export function canonicalLabel(label: string): string {
  const raw = label.trim()
  if (!raw) return raw
  const n = normalize(raw)
  if (ALIASES[n]) return ALIASES[n]
  const hit = PREFERRED_ORDER.find((p) => normalize(p) === n)
  return hit || raw
}

export function isPreferredLabel(label: string): boolean {
  return preferredIndex.has(normalize(canonicalLabel(label)))
}

export function isAdultLabel(label: string): boolean {
  return /(?:\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw|hot\s*xxx|onlyfans|playboy/i.test(
    label,
  )
}

export function cleanChannelTitle(raw: string): string {
  let t = raw.trim()
  if (!t) return t
  t = t.replace(/\[[^\]]*]/g, '')
  t = t.replace(/\(\s*\d{3,4}\s*p\s*\)/gi, '')
  t = t.replace(/\(\s*(HD|FHD|UHD|4K|SD|HEVC|H\.?265)\s*\)/gi, '')
  t = t.replace(
    /\(\s*(España|Mexico|México|Argentina|Chile|Peru|Perú|Colombia|Brasil|USA|UK|LatAm|International)[^)]*\)/gi,
    '',
  )
  t = t.replace(/[\u{1F1E6}-\u{1F1FF}]{2}/gu, '')
  t = t.replace(/\s{2,}/g, ' ').trim()
  if (!t) return raw.trim()
  return t
    .split(/\s+/)
    .map((w) => (w.length <= 2 ? w.toUpperCase() : w[0].toUpperCase() + w.slice(1).toLowerCase()))
    .join(' ')
}

function attr(line: string, key: string): string {
  const m = line.match(new RegExp(`${key}="([^"]*)"`, 'i'))
  return m?.[1]?.trim() || ''
}

export function parseM3u(text: string): Channel[] {
  const lines = text.split(/\r?\n/)
  const out: Channel[] = []
  let i = 0
  let n = 0
  while (i < lines.length) {
    const line = lines[i]
    if (line.startsWith('#EXTINF')) {
      const comma = line.lastIndexOf(',')
      const nameRaw = comma >= 0 ? line.slice(comma + 1).trim() : `Canal ${n + 1}`
      const groupRaw = attr(line, 'group-title') || 'Variados'
      const group = canonicalLabel(groupRaw)
      const url = (lines[i + 1] || '').trim()
      if (url.startsWith('http') && isPreferredLabel(group)) {
        n += 1
        const tvgId = attr(line, 'tvg-id')
        out.push({
          id: tvgId ? `tvg:${tvgId}` : `m3u:${nameRaw.toLowerCase()}:${n}`,
          number: n,
          name: cleanChannelTitle(nameRaw) || nameRaw,
          logo: attr(line, 'tvg-logo') || undefined,
          group,
          tvgId,
          url,
        })
      }
      i += 2
      continue
    }
    i += 1
  }
  return out
}

/** Daniel65 VOD: keep every group-title (no live preferred filter). */
export function parseVodM3u(text: string, kind: 'movies' | 'series'): Channel[] {
  const lines = text.split(/\r?\n/)
  const out: Channel[] = []
  let i = 0
  let n = 0
  const prefix = kind === 'movies' ? 'movie' : 'series'
  while (i < lines.length) {
    const line = lines[i]
    if (line.startsWith('#EXTINF')) {
      const comma = line.lastIndexOf(',')
      const nameRaw = comma >= 0 ? line.slice(comma + 1).trim() : `Título ${n + 1}`
      const group = (attr(line, 'group-title') || 'Variados').trim() || 'Variados'
      const url = (lines[i + 1] || '').trim()
      if (url.startsWith('http')) {
        n += 1
        const tvgId = attr(line, 'tvg-id')
        const tvgName = attr(line, 'tvg-name') || nameRaw
        out.push({
          id: tvgId ? `${prefix}:tvg:${tvgId}` : `${prefix}:${n}:${tvgName.toLowerCase()}`,
          number: n,
          name: tvgName.trim() || nameRaw,
          logo: attr(line, 'tvg-logo') || undefined,
          group,
          tvgId,
          url,
        })
      }
      i += 2
      continue
    }
    i += 1
  }
  return out
}

export function sortCategories(channels: Channel[]): string[] {
  const counts = new Map<string, number>()
  for (const ch of channels) {
    counts.set(ch.group, (counts.get(ch.group) || 0) + 1)
  }
  return PREFERRED_ORDER.filter((name) => (counts.get(name) || 0) > 0)
}

export function defaultCategory(cats: string[]): string | undefined {
  return cats.find((c) => !isAdultLabel(c)) || cats[0]
}
