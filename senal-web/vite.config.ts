import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ command }) => {
  /** Production → /web/ on the same VPS public folder. Dev stays at /. Override: VITE_BASE=/ */
  const base = process.env.VITE_BASE || (command === 'build' ? '/web/' : '/')

  return {
    base,
    plugins: [react()],
    server: {
      host: true,
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://185.192.20.245:3000',
          changeOrigin: true,
        },
      },
    },
    preview: {
      host: true,
      port: 4173,
    },
  }
})
