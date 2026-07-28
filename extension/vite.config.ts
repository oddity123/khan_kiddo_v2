import {readFileSync, writeFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {defineConfig, loadEnv} from 'vite'

function mergeWebOriginHostPermission(
    manifest: Record<string, unknown>,
    webOrigin: string | undefined,
): void {
  const trimmed = webOrigin?.trim()
  if (!trimmed) {
    return
  }
  let permissionUrl: string
  try {
    const u = new URL(trimmed)
    permissionUrl = `${u.protocol}//${u.host}/*`
  } catch {
    console.warn('[vite] VITE_KK_WEB_ORIGIN 非法，跳过写入 host_permissions:', webOrigin)
    return
  }
  const existing = manifest.host_permissions
  const list = Array.isArray(existing) ? [...(existing as string[])] : []
  if (!list.includes(permissionUrl)) {
    list.push(permissionUrl)
  }
  manifest.host_permissions = list
}

export default defineConfig(({mode}) => {
  const env = loadEnv(mode, __dirname, '')
  const webOrigin = env.VITE_KK_WEB_ORIGIN

  return {
    base: './',
    build: {
      outDir: 'dist',
      emptyOutDir: true,
      rollupOptions: {
        input: {
          background: resolve(__dirname, 'src/background/index.ts'),
          'chatgpt-share': resolve(__dirname, 'src/content/chatgpt-share.ts'),
          popup: resolve(__dirname, 'popup.html'),
        },
        output: {
          entryFileNames: '[name].js',
          chunkFileNames: 'chunks/[name]-[hash].js',
          assetFileNames: 'assets/[name]-[hash][extname]',
        },
      },
    },
    plugins: [
      {
        name: 'write-extension-manifest',
        closeBundle() {
          const raw = readFileSync(resolve(__dirname, 'manifest.json'), 'utf8')
          const manifest = JSON.parse(raw) as Record<string, unknown>
          mergeWebOriginHostPermission(manifest, webOrigin)
          if (mode === 'production') {
            const existing = Array.isArray(manifest.host_permissions)
                ? (manifest.host_permissions as string[])
                : []
            manifest.host_permissions = existing.filter(
                (p) => !p.includes('localhost') && !p.includes('127.0.0.1'),
            )
            mergeWebOriginHostPermission(manifest, webOrigin)
          }
          writeFileSync(
              resolve(__dirname, 'dist/manifest.json'),
              `${JSON.stringify(manifest, null, 2)}\n`,
              'utf8',
          )
        },
      },
    ],
  }
})
