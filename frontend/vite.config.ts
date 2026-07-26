import { fileURLToPath, URL } from 'node:url'

import prerender from '@prerenderer/rollup-plugin'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const SITE_ORIGIN = 'https://khankiddo.top'

export default defineConfig({
  plugins: [
    vue(),
    prerender({
      routes: ['/', '/login', '/register', '/feedback'],
      renderer: '@prerenderer/renderer-puppeteer',
      rendererOptions: {
        headless: true,
        renderAfterDocumentEvent: 'prerender-ready',
        maxConcurrentRoutes: 1,
        timeout: 30_000,
      },
      postProcess(renderedRoute) {
        renderedRoute.html = renderedRoute.html
          .replace(/http:/gi, 'https:')
          .replace(
            /(https:\/\/)?(localhost|127\.0\.0\.1):\d*/gi,
            SITE_ORIGIN,
          )
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
