/** SEÑAL Web — same VPS as the Android APK. */

const envBase = (import.meta.env.VITE_API_BASE as string | undefined)?.replace(/\/$/, '')

/**
 * Same-origin on the VPS (`/web` + `/api`) → empty string.
 * Local `npm run dev` → absolute VPS URL (or Vite proxy if you call `/api` via empty + proxy).
 */
export const API_BASE =
  envBase !== undefined
    ? envBase
    : import.meta.env.PROD
      ? ''
      : 'http://185.192.20.245:3000'

const assetBase = import.meta.env.BASE_URL.endsWith('/')
  ? import.meta.env.BASE_URL
  : `${import.meta.env.BASE_URL}/`

export const CATALOG_URL = `${assetBase}catalog/lista_fusionada.m3u.gz`
export const MOVIES_VOD_URL = `${assetBase}vod/daniel65_peliculas.m3u.gz`
export const SERIES_VOD_URL = `${assetBase}vod/daniel65_series.m3u.gz`
export const APP_NAME = 'SEÑAL Web'
export const APP_VERSION = '1.9.3'
export const PLATFORM = 'web'
