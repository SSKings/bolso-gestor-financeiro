import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Dev: host true → abre também de outros aparelhos da rede (celular), em http://<ip-da-máquina>:5173.
// A API é chamada pelo proxy /api (mesma origem): o celular não precisa enxergar a porta 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: process.env.API_URL ?? 'http://localhost:8080', changeOrigin: false } },
  },
  preview: { host: true, port: 4173, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.test.{ts,tsx}', 'src/vite-env.d.ts'],
      thresholds: { lines: 96, branches: 89, functions: 94, statements: 95 },   // perto do atual (97,8 / 91,0 / 96,3 / 96,2)
    },
  },
})
