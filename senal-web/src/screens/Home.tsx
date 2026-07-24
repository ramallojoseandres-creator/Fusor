import { useEffect, useMemo, useState } from 'react'
import { loadCatalog } from '../catalog/load'
import { defaultCategory, sortCategories, type Channel } from '../catalog/rules'
import { SenalBrand } from '../components/Brand'
import { clearSession, getUsername } from '../api/auth'

export type HomeSection = 'hub' | 'live' | 'movies' | 'series' | 'settings'

type Props = {
  section: HomeSection
  onSection: (s: HomeSection) => void
  onPlay: (ch: Channel, neighbors: Channel[]) => void
  onLogout: () => void
}

export function HomeScreen({ section, onSection, onPlay, onLogout }: Props) {
  const [channels, setChannels] = useState<Channel[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [clock, setClock] = useState(() =>
    new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }),
  )

  useEffect(() => {
    const id = window.setInterval(() => {
      setClock(new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' }))
    }, 15_000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const list = await loadCatalog()
        if (!cancelled) setChannels(list)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Error de catálogo')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const preview = channels[0]

  if (section === 'live') {
    return (
      <LiveGuide
        channels={channels}
        loading={loading}
        error={error}
        onBack={() => onSection('hub')}
        onPlay={onPlay}
      />
    )
  }

  if (section === 'settings') {
    return (
      <SettingsPane
        onBack={() => onSection('hub')}
        onLogout={() => {
          clearSession()
          onLogout()
        }}
      />
    )
  }

  if (section === 'movies' || section === 'series') {
    return (
      <div className="screen hub">
        <div className="bg-bleed dim" />
        <header className="hub-top">
          <SenalBrand size={32} letterSpacing={3} subtitle="IPTV" />
          <button className="ghost-link" onClick={() => onSection('hub')}>
            ← Volver
          </button>
        </header>
        <div className="placeholder-pane">
          <h2>{section === 'movies' ? 'Películas' : 'Series'}</h2>
          <p>Misma sección que en la app — catálogo VOD en el siguiente paso.</p>
        </div>
      </div>
    )
  }

  return (
    <div className="screen hub">
      <div className="bg-bleed" />
      <div className="hub-veil" />
      <header className="hub-top">
        <SenalBrand size={36} letterSpacing={3} subtitle="IPTV" />
        <div className="clock">{clock}</div>
      </header>

      <div className="hub-hero">
        <h1 className="hero-title">{preview?.name || 'SEÑAL en vivo'}</h1>
        <button className="continue-link" onClick={() => onSection('live')}>
          Continuar viendo • <span>OK</span>
        </button>
      </div>

      <div className="hub-tiles">
        {(
          [
            ['live', 'VIVO', 'live'],
            ['movies', 'PELÍCULAS', 'movies'],
            ['series', 'SERIES', 'series'],
            ['settings', 'AJUSTES', 'settings'],
          ] as const
        ).map(([key, label, icon]) => (
          <button key={key} className="hub-tile" onClick={() => onSection(key)}>
            <TileIcon kind={icon} />
            <span>{label}</span>
          </button>
        ))}
      </div>

      {loading ? <p className="hub-status">Cargando catálogo…</p> : null}
      {error ? <p className="hub-status error">{error}</p> : null}
    </div>
  )
}

function TileIcon({ kind }: { kind: string }) {
  return <span className={`tile-glyph ${kind}`} aria-hidden />
}

function LiveGuide({
  channels,
  loading,
  error,
  onBack,
  onPlay,
}: {
  channels: Channel[]
  loading: boolean
  error: string | null
  onBack: () => void
  onPlay: (ch: Channel, neighbors: Channel[]) => void
}) {
  const cats = useMemo(() => sortCategories(channels), [channels])
  const [selected, setSelected] = useState<string>('')
  const [query, setQuery] = useState('')

  useEffect(() => {
    if (!selected) setSelected(defaultCategory(cats) || '')
  }, [cats, selected])

  const list = useMemo(() => {
    const base = channels.filter((c) => c.group === selected)
    const q = query.trim().toLowerCase()
    if (!q) return base
    return base.filter((c) => c.name.toLowerCase().includes(q))
  }, [channels, selected, query])

  return (
    <div className="screen live">
      <div className="bg-bleed dim" />
      <aside className="cat-col">
        <button className="brand-pill" onClick={onBack}>
          <SenalBrand size={18} letterSpacing={1} />
        </button>
        <div className="cat-scroll">
          {cats.map((c) => (
            <button
              key={c}
              className={`cat-row ${c === selected ? 'on' : ''}`}
              onClick={() => setSelected(c)}
            >
              <i />
              {c}
            </button>
          ))}
        </div>
      </aside>
      <section className="ch-col">
        <div className="ch-toolbar">
          <input
            placeholder="Buscar canal…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <button className="ghost-link" onClick={onBack}>
            Cerrar
          </button>
        </div>
        {loading ? <p className="muted">Cargando…</p> : null}
        {error ? <p className="error">{error}</p> : null}
        <div className="ch-scroll">
          {list.map((ch) => (
            <button
              key={ch.id}
              className="ch-row"
              onClick={() => onPlay(ch, list)}
            >
              <span className="num">{ch.number}</span>
              <span className="logo">
                {ch.logo ? <img src={ch.logo} alt="" loading="lazy" /> : ch.name.slice(0, 2)}
              </span>
              <span className="meta">
                <strong>{ch.name}</strong>
                <em>Toca para reproducir</em>
              </span>
            </button>
          ))}
          {!loading && list.length === 0 ? (
            <p className="muted">Sin canales en esta categoría</p>
          ) : null}
        </div>
      </section>
    </div>
  )
}

function SettingsPane({ onBack, onLogout }: { onBack: () => void; onLogout: () => void }) {
  return (
    <div className="screen hub">
      <div className="bg-bleed dim" />
      <header className="hub-top">
        <SenalBrand size={32} letterSpacing={3} subtitle="AJUSTES" />
        <button className="ghost-link" onClick={onBack}>
          ← Volver
        </button>
      </header>
      <div className="settings-grid">
        <div className="settings-card">
          <h3>Usuario</h3>
          <p>{getUsername() || '—'}</p>
        </div>
        <div className="settings-card">
          <h3>Cliente</h3>
          <p>SEÑAL Web 1.9.0</p>
        </div>
        <button className="settings-card danger" onClick={onLogout}>
          <h3>Salir</h3>
          <p>Cerrar sesión</p>
        </button>
      </div>
    </div>
  )
}
