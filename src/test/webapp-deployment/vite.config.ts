import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  base: '/deployment/',
  build: {
    outDir: path.resolve(__dirname, '../resources/deployment-ui'),
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
        target: `http://localhost:${process.env.VITE_DEPLOYMENT_PORT ?? 8091}`,
        changeOrigin: true,
      },
    },
  },
})
