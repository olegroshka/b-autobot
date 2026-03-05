import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    // Output directly into WireMock's __files/blotter/ so the Maven test run
    // picks it up automatically without a copy step.
    outDir: path.resolve(__dirname, '../../resources/wiremock/__files/blotter'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        // Fixed names (no content hash) so WireMock stubs can reference them
        // by stable paths rather than needing per-build updates.
        entryFileNames: 'assets/index.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
  server: {
    // In 'npm run dev' mode, proxy /api calls to the WireMock port.
    // Set VITE_WIREMOCK_PORT env var or pass the port on the command line.
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.VITE_WIREMOCK_PORT ?? 8080}`,
        changeOrigin: true,
      },
    },
  },
})
