import {defineStore} from 'pinia'
import {ref} from 'vue'

import {fetchSiteInfo, type SiteInfo} from '@/api/site'

/**
 * 与 backend application.yml 默认备案一致。
 * 先本地展示，再被 /api/site 覆盖——避免预渲染/接口失败时页脚空白。
 */
const FALLBACK_SITE_INFO: SiteInfo = {
  icpNumber: '鄂ICP备2026025924号',
  icpUrl: 'https://beian.miit.gov.cn/',
  psbNumber: '鄂公网安备42010202002911号',
  psbUrl: 'https://beian.mps.gov.cn/#/query/webSearch?code=42010202002911',
}

export const useSiteStore = defineStore('site', () => {
  const info = ref<SiteInfo | null>({...FALLBACK_SITE_INFO})
  const loaded = ref(false)
  let loading: Promise<void> | null = null

  async function ensureLoaded() {
    if (loaded.value) {
      return
    }
    if (!loading) {
      loading = fetchSiteInfo()
        .then((response) => {
          info.value = response.data
          loaded.value = true
        })
        .catch(() => {
          info.value = {...FALLBACK_SITE_INFO}
          loaded.value = true
        })
        .finally(() => {
          loading = null
        })
    }
    await loading
  }

  return {info, loaded, ensureLoaded}
})
