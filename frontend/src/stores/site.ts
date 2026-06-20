import {defineStore} from 'pinia'
import {ref} from 'vue'

import {fetchSiteInfo, type SiteInfo} from '@/api/site'

export const useSiteStore = defineStore('site', () => {
  const info = ref<SiteInfo | null>(null)
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
          info.value = null
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
