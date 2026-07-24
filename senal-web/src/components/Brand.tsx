type Props = {
  size?: number
  letterSpacing?: number
  subtitle?: string
  subtitleSize?: number
  className?: string
}

/** Wordmark SEÑAL — Ñ cyan (same as APK mockups). */
export function SenalBrand({
  size = 42,
  letterSpacing = 3,
  subtitle,
  subtitleSize = 13,
  className,
}: Props) {
  return (
    <div className={className} style={{ lineHeight: 1 }}>
      <div
        className="senal-brand"
        style={{ fontSize: size, letterSpacing }}
        aria-label="SEÑAL"
      >
        <span>SE</span>
        <span className="n">Ñ</span>
        <span>AL</span>
      </div>
      {subtitle ? (
        <div className="senal-sub" style={{ fontSize: subtitleSize }}>
          {subtitle}
        </div>
      ) : null}
    </div>
  )
}
