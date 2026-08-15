import { existsSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'

import prerender from '@prerenderer/rollup-plugin'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

const SITE_ORIGIN = 'https://khankiddo.top'

const SYSTEM_CHROME =
  process.platform === 'darwin'
    ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
    : process.platform === 'win32'
      ? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
      : '/usr/bin/google-chrome'

const chromeExecutable = existsSync(SYSTEM_CHROME) ? SYSTEM_CHROME : undefined

export default defineConfig({
  plugins: [
    vue(),
    prerender({
      routes: ['/', '/login', '/register', '/feedback', '/privacy/extension'],
      renderer: '@prerenderer/renderer-puppeteer',
      rendererOptions: {
        headless: true,
        renderAfterDocumentEvent: 'prerender-ready',
        maxConcurrentRoutes: 1,
        timeout: 30_000,
        ...(chromeExecutable ? { executablePath: chromeExecutable } : {}),
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
