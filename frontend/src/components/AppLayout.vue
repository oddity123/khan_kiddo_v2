<script setup lang="ts">
import AppNavbar from '@/components/AppNavbar.vue'
import SiteBeian from '@/components/SiteBeian.vue'
import {RouterView} from 'vue-router'
</script>

<template>
  <div class="layout">
    <div class="kk-page-bg" aria-hidden="true"/>
    <AppNavbar/>
    <main class="page-main">
      <div class="kk-page-shell">
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
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow-x: hidden;
}

.page-main {
  flex: 1;
  padding: var(--kk-navbar-offset) 0 2.5rem;
  position: relative;
  z-index: 1;
}

.page-footer {
  padding: 1rem 0 1.5rem;
  position: relative;
  z-index: 1;
}
</style>
