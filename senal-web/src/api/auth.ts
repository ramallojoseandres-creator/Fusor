import { API_BASE, APP_NAME, PLATFORM } from '../config'

const TOKEN_KEY = 'senal.token'
const USER_KEY = 'senal.user'
const DEVICE_KEY = 'senal.deviceId'

export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = `web-${crypto.randomUUID()}`
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUsername(): string {
  return localStorage.getItem(USER_KEY) || ''
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

type LoginJson = {
  ok?: boolean
  token?: string
  accessToken?: string
  jwt?: string
  error?: string
  message?: string
  user?: { id?: string; username?: string; role?: string }
}

export async function login(username: string, password: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Device-Id': getDeviceId(),
      'X-Device-Name': APP_NAME,
      'X-Device-Platform': PLATFORM,
    },
    body: JSON.stringify({
      username: username.trim(),
      password,
      deviceId: getDeviceId(),
      deviceName: APP_NAME,
      platform: PLATFORM,
    }),
  })
  const data = (await res.json().catch(() => ({}))) as LoginJson
  const token = data.token || data.accessToken || data.jwt
  if (!res.ok || !token) {
    const msg = data.message || data.error || 'No se pudo iniciar sesión'
    if (/unable to resolve|failed to connect|timeout|network/i.test(msg)) {
      throw new Error('Sin conexión con el servidor')
    }
    throw new Error(msg === 'INVALID_CREDENTIALS' ? 'Usuario o contraseña incorrectos' : msg)
  }
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, data.user?.username || username.trim())
}
