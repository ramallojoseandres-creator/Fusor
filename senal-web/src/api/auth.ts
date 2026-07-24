import { API_BASE, APP_NAME, PLATFORM } from '../config'

const TOKEN_KEY = 'senal.token'
const USER_KEY = 'senal.user'
const DEVICE_KEY = 'senal.deviceId'

/** Works on HTTP / older WebViews where crypto.randomUUID is missing. */
function newDeviceId(): string {
  const c = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined
  if (c && typeof c.randomUUID === 'function') {
    return `web-${c.randomUUID()}`
  }
  const bytes = new Uint8Array(16)
  if (c && typeof c.getRandomValues === 'function') {
    c.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 16; i++) bytes[i] = Math.floor(Math.random() * 256)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  return `web-${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = newDeviceId()
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
