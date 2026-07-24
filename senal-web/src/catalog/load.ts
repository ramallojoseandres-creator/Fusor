import { gunzipSync } from 'fflate'
import { CATALOG_URL } from '../config'
import { parseM3u, type Channel } from './rules'

let cache: Channel[] | null = null
let inflight: Promise<Channel[]> | null = null

export async function loadCatalog(): Promise<Channel[]> {
  if (cache) return cache
  if (inflight) return inflight
  inflight = (async () => {
    const res = await fetch(CATALOG_URL)
    if (!res.ok) throw new Error('No se pudo cargar el catálogo')
    const buf = new Uint8Array(await res.arrayBuffer())
    const text = new TextDecoder().decode(gunzipSync(buf))
    cache = parseM3u(text)
    return cache
  })()
  try {
    return await inflight
  } finally {
    inflight = null
  }
}
