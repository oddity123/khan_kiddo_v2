<script setup lang="ts">
import {computed} from 'vue'

import {renderMarkdown} from '@/utils/markdown'

import '@/styles/markdown.css'

const props = withDefaults(
    defineProps<{
      content: string
      inverted?: boolean
    }>(),
    {
      inverted: false,
    },
)

const html = computed(() => renderMarkdown(props.content))
</script>

<template>
  <div
      class="markdown-body"
      :class="{'markdown-body--inverted': inverted}"
      v-html="html"
  />
</template>
