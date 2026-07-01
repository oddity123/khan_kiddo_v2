import path from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

const frontendDir = fileURLToPath(new URL('.', import.meta.url))
const repoRoot = path.resolve(frontendDir, '..')

export default defineConfig(({ mode }) => {
  // 与仓库根目录 .env 中 PORT 对齐；也可在 frontend/.env.development 设置 VITE_DEV_API_PORT
  const rootEnv = loadEnv(mode, repoRoot, '')
  const frontendEnv = loadEnv(mode, frontendDir, '')
  const backendPort = rootEnv.PORT || frontendEnv.VITE_DEV_API_PORT || '8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: `http://localhost:${backendPort}`,
          changeOrigin: true,
        },
      },
    },
  }
})
