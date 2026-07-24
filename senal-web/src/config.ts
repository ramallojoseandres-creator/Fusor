/** SEÑAL Web — same VPS as the Android APK. */
export const API_BASE =
  (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '') ||
  'http://185.192.20.245:3000'

export const CATALOG_URL = '/catalog/lista_fusionada.m3u.gz'
export const MOVIES_VOD_URL = '/vod/daniel65_peliculas.m3u.gz'
export const SERIES_VOD_URL = '/vod/daniel65_series.m3u.gz'
export const APP_NAME = 'SEÑAL Web'
export const APP_VERSION = '1.9.1'
export const PLATFORM = 'web'
