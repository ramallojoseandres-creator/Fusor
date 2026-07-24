import { useEffect, useRef, useState } from 'react'
import Hls from 'hls.js'
import type { Channel } from '../catalog/rules'
import { SenalBrand } from '../components/Brand'

type Props = {
  channel: Channel
  neighbors: Channel[]
  onBack: () => void
  onChange: (ch: Channel) => void
}

export function PlayerScreen({ channel, neighbors, onBack, onChange }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [status, setStatus] = useState('Conectando…')
  const [showGuide, setShowGuide] = useState(false)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    let hls: Hls | null = null
    setStatus('Conectando…')

    const url = channel.url
    const isHls = /\.m3u8(\?|$)/i.test(url) || url.includes('/hls')

    if (isHls && Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: true })
      hls.loadSource(url)
      hls.attachMedia(video)
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setStatus('')
        void video.play().catch(() => setStatus('Pulsa para reproducir'))
      })
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          setStatus(
            data.type === Hls.ErrorTypes.NETWORK_ERROR
              ? 'Stream no disponible (red/CORS)'
              : 'Error de reproducción',
          )
        }
      })
    } else if (video.canPlayType('application/vnd.apple.mpegurl') && isHls) {
      video.src = url
      void video.play().then(() => setStatus('')).catch(() => setStatus('Pulsa para reproducir'))
    } else {
      video.src = url
      void video
        .play()
        .then(() => setStatus(''))
        .catch(() => setStatus('Pulsa para reproducir / formato no soportado'))
    }

    return () => {
      hls?.destroy()
      video.removeAttribute('src')
      video.load()
    }
  }, [channel.id, channel.url])

  const idx = neighbors.findIndex((n) => n.id === channel.id)

  return (
    <div className="screen player">
      <video
        ref={videoRef}
        className="player-video"
        controls
        playsInline
        autoPlay
        onClick={() => status && videoRef.current?.play()}
      />
      <div className="player-hud">
        <button className="ghost-link" onClick={onBack}>
          ← Guía
        </button>
        <SenalBrand size={20} letterSpacing={2} />
        <div className="player-title">
          <strong>{channel.name}</strong>
          <span>{channel.group}</span>
        </div>
        <div className="player-actions">
          <button
            className="ghost-link"
            disabled={idx <= 0}
            onClick={() => idx > 0 && onChange(neighbors[idx - 1])}
          >
            ‹ Ant
          </button>
          <button
            className="ghost-link"
            disabled={idx < 0 || idx >= neighbors.length - 1}
            onClick={() => idx >= 0 && idx < neighbors.length - 1 && onChange(neighbors[idx + 1])}
          >
            Sig ›
          </button>
          <button className="ghost-link" onClick={() => setShowGuide((v) => !v)}>
            Canales
          </button>
        </div>
      </div>
      {status ? <div className="player-status">{status}</div> : null}
      {showGuide ? (
        <aside className="player-guide">
          {neighbors.map((ch) => (
            <button
              key={ch.id}
              className={ch.id === channel.id ? 'on' : ''}
              onClick={() => {
                onChange(ch)
                setShowGuide(false)
              }}
            >
              {ch.name}
            </button>
          ))}
        </aside>
      ) : null}
    </div>
  )
}
