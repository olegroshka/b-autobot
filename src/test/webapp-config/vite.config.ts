import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  base: '/config-service/',
  build: {
    // Output directly into src/test/resources/config-service-ui/
    // so MockConfigServer's static file handler can serve it.
    outDir: path.resolve(__dirname, '../resources/config-service-ui'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/index.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name].[ext]',
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: `http://localhost:${process.env.VITE_CONFIG_PORT ?? 8090}`,
        changeOrigin: true,
      },
    },
  },
})
