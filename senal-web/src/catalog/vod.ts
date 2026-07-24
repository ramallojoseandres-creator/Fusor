import { gunzipSync } from 'fflate'
import { MOVIES_VOD_URL, SERIES_VOD_URL } from '../config'
import { parseVodM3u, type Channel } from './rules'

export type VodKind = 'movies' | 'series'

type Bundle = {
  items: Channel[]
  categories: string[]
  byCat: Map<string, Channel[]>
}

const cache: Partial<Record<VodKind, Bundle>> = {}
const inflight: Partial<Record<VodKind, Promise<Bundle>>> = {}

function urlFor(kind: VodKind) {
  return kind === 'movies' ? MOVIES_VOD_URL : SERIES_VOD_URL
}

function buildBundle(items: Channel[]): Bundle {
  const byCat = new Map<string, Channel[]>()
  for (const ch of items) {
    const key = ch.group || 'Variados'
    const arr = byCat.get(key)
    if (arr) arr.push(ch)
    else byCat.set(key, [ch])
  }
  const categories = [...byCat.keys()].filter(
    (c) => c && !c.toLowerCase().includes('.m3u') && !c.toLowerCase().includes('vod/'),
  )
  return { items, categories, byCat }
}

async function loadBundle(kind: VodKind): Promise<Bundle> {
  const hit = cache[kind]
  if (hit) return hit
  const pending = inflight[kind]
  if (pending) return pending

  const job = (async () => {
    const res = await fetch(urlFor(kind))
    if (!res.ok) throw new Error('No se pudo cargar el catálogo VOD')
    const buf = new Uint8Array(await res.arrayBuffer())
    const text = new TextDecoder().decode(gunzipSync(buf))
    const items = parseVodM3u(text, kind)
    const bundle = buildBundle(items)
    cache[kind] = bundle
    return bundle
  })()

  inflight[kind] = job
  try {
    return await job
  } finally {
    delete inflight[kind]
  }
}

export async function loadVod(kind: VodKind): Promise<Bundle> {
  return loadBundle(kind)
}

export function vodCategories(bundle: Bundle): string[] {
  return bundle.categories
}

export function vodItems(
  bundle: Bundle,
  category: string,
  query = '',
): Channel[] {
  const base = bundle.byCat.get(category) || []
  const q = query.trim().toLowerCase()
  if (!q) return base
  return base.filter((c) => c.name.toLowerCase().includes(q))
}
