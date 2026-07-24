import { useEffect, useState } from 'react'
import { SenalBrand } from '../components/Brand'

type Props = { onDone: () => void }

export function SplashScreen({ onDone }: Props) {
  const [visible, setVisible] = useState(true)

  useEffect(() => {
    const t1 = window.setTimeout(() => setVisible(false), 1600)
    const t2 = window.setTimeout(onDone, 2100)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
    }
  }, [onDone])

  return (
    <div className={`screen splash ${visible ? 'in' : 'out'}`}>
      <div className="splash-glow" />
      <SenalBrand size={64} letterSpacing={6} subtitle="IPTV" />
      <p className="splash-tag">En vivo · Películas · Series</p>
    </div>
  )
}
