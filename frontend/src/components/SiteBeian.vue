<script setup lang="ts">
import {computed, onMounted} from 'vue'

import {useSiteStore} from '@/stores/site'

withDefaults(
  defineProps<{
    variant?: 'footer' | 'auth'
  }>(),
  {
    variant: 'footer',
  },
)

const site = useSiteStore()

onMounted(() => {
  void site.ensureLoaded()
})

const hasBeian = computed(
  () => Boolean(site.info?.icpNumber) || Boolean(site.info?.psbNumber),
)
</script>

<template>
  <div
    v-if="hasBeian"
    class="site-beian"
    :class="`site-beian--${variant}`"
  >
    <p v-if="site.info?.icpNumber" class="site-beian__line">
      <a
        :href="site.info.icpUrl"
        target="_blank"
        rel="noopener noreferrer"
      >{{ site.info.icpNumber }}</a>
    </p>
    <p v-if="site.info?.psbNumber" class="site-beian__line">
      <a
        :href="site.info.psbUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        <img
          src="/images/ghs.png"
          alt=""
          class="site-beian__psb-icon"
        />
        <span>{{ site.info.psbNumber }}</span>
      </a>
    </p>
  </div>
</template>

<style scoped>
.site-beian {
  text-align: center;
  font-family: var(--kk-font-body);
}

.site-beian__line {
  margin: 0;
}

.site-beian__line + .site-beian__line {
  margin-top: 0.25rem;
}

.site-beian a {
  color: var(--kk-color-text-muted);
  text-decoration: none;
}

.site-beian a:hover {
  color: var(--kk-color-text-secondary);
  text-decoration: underline;
}

.site-beian__psb-icon {
  height: 20px;
  width: auto;
  vertical-align: middle;
  margin-right: 4px;
}

.site-beian--footer {
  font-size: 0.875rem;
}

.site-beian--auth {
  margin-top: 1rem;
  font-size: 0.6875rem;
  line-height: 1.4;
}

.site-beian--auth .site-beian__psb-icon {
  height: 14px;
  margin-right: 3px;
}

.site-beian--auth .site-beian__line + .site-beian__line {
  margin-top: 0.125rem;
}
</style>
