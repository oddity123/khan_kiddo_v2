<script setup lang="ts">
import {computed} from 'vue'
import {RouterView, useRoute} from 'vue-router'

import AppNavbar from '@/components/AppNavbar.vue'
import SiteBeian from '@/components/SiteBeian.vue'

const route = useRoute()
const isImmersive = computed(() => Boolean(route.meta.immersive))
</script>

<template>
  <div class="layout" :class="{'layout--immersive': isImmersive}">
    <div class="kk-page-bg" aria-hidden="true"/>
    <AppNavbar/>
    <main class="page-main">
      <div class="kk-page-shell page-main-shell">
        <RouterView />
      </div>
    </main>
    <footer class="page-footer">
      <SiteBeian variant="footer" />
    </footer>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
}

.page-main {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--kk-navbar-offset) 0 0;
  position: relative;
  z-index: 1;
}

.page-main-shell {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding-bottom: 1.5rem;
}

.page-footer {
  flex-shrink: 0;
  padding: 1rem 0 1.5rem;
  position: relative;
  z-index: 1;
}

.layout--immersive {
  height: 100dvh;
  overflow: hidden;
}

.layout--immersive .page-main {
  flex: 1 1 0;
  overflow: hidden;
}

.layout--immersive .page-main-shell {
  flex: 1 1 0;
  overflow: hidden;
  padding-bottom: 0;
}
</style>
