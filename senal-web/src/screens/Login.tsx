import { useEffect, useState, type FormEvent } from 'react'
import { login } from '../api/auth'
import { SenalBrand } from '../components/Brand'

type Props = { onLoggedIn: () => void }

function clockNow() {
  return new Date().toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
}

export function LoginScreen({ onLoggedIn }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPass, setShowPass] = useState(false)
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [clock, setClock] = useState(clockNow)

  useEffect(() => {
    const id = window.setInterval(() => setClock(clockNow()), 15_000)
    return () => clearInterval(id)
  }, [])

  async function submit(e?: FormEvent) {
    e?.preventDefault()
    if (loading || success) return
    if (!username.trim() || !password) {
      setError('Introduce usuario y contraseña')
      return
    }
    setLoading(true)
    setError(null)
    try {
      await login(username, password)
      setSuccess(true)
      window.setTimeout(onLoggedIn, 550)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="screen login">
      <div className="bg-bleed" />
      <div className="login-top">
        <SenalBrand size={44} letterSpacing={3} />
        <div className="clock">{clock}</div>
      </div>

      <form className="glass-panel" onSubmit={submit}>
        <label className="field">
          <span>Usuario</span>
          <input
            autoComplete="username"
            value={username}
            onChange={(e) => {
              setUsername(e.target.value)
              setError(null)
            }}
          />
        </label>
        <label className="field">
          <span>Contraseña</span>
          <div className="pass-row">
            <input
              type={showPass ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value)
                setError(null)
              }}
            />
            <button type="button" className="ghost" onClick={() => setShowPass((v) => !v)}>
              {showPass ? '◉' : '◎'}
            </button>
          </div>
        </label>
        {error ? <p className="error">{error}</p> : null}
        <button className="btn-cyan" type="submit" disabled={loading || success}>
          {success ? 'LISTO' : loading ? 'CONECTANDO…' : 'ENTRAR'}
        </button>
        <p className="hint">Inicia sesión para ver en vivo</p>
      </form>

      {success ? (
        <div className="success-veil">
          <span>✓</span>
        </div>
      ) : null}
    </div>
  )
}
