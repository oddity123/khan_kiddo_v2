<script setup lang="ts">
import {computed} from 'vue'
import {RouterView, useRoute} from 'vue-router'

import AppLayout from '@/components/AppLayout.vue'

const route = useRoute()
const isAdminShell = computed(() => route.matched.some((record) => record.meta.adminShell))
</script>

<template>
  <RouterView v-if="isAdminShell"/>
  <AppLayout v-else/>
</template>
