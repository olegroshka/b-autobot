import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Output directly into WireMock's __files/blotter/ so the
    // Maven test run picks it up automatically without a copy step.
    outDir: path.resolve(__dirname, '../../resources/wiremock/__files/blotter'),
    emptyOutDir: true,
  },
  server: {
    // In 'npm run dev' mode, proxy /api calls to the WireMock port.
    // Set VITE_WIREMOCK_PORT env var to match the WireMock port printed
    // at startup, or use a fixed port by launching WireMock with a fixed port.
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.VITE_WIREMOCK_PORT || 8080}`,
        changeOrigin: true,
      },
    },
  },
})
