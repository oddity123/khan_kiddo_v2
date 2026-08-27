<script setup lang="ts">
import {computed} from 'vue'

import type {SentenceEdit} from '@/types/conversation'
import {
  buildCorrectedSegments,
  buildOriginalSegments,
  type TextSegment,
} from '@/utils/editAnnotation'

const props = defineProps<{
  side: 'original' | 'corrected'
  text: string
  tokens?: string[]
  edits?: SentenceEdit[]
}>()

const segments = computed<TextSegment[] | null>(() => {
  const edits = props.edits
  const tokens = props.tokens
  if (!edits?.length || !tokens?.length) {
    return null
  }
  if (props.side === 'original') {
    for (const e of edits) {
      if (e.oStart < 0 || e.oEnd < e.oStart || e.oEnd > tokens.length) {
        return null
      }
    }
    return buildOriginalSegments(tokens, edits)
  }
  for (const e of edits) {
    if (e.cStart < 0 || e.cEnd < e.cStart || e.cEnd > tokens.length) {
      return null
    }
  }
  return buildCorrectedSegments(tokens, edits)
})

function opTitle(op?: SentenceEdit['op']): string | undefined {
  if (op === 'R') {
    return '替换'
  }
  if (op === 'M') {
    return '补充'
  }
  if (op === 'U') {
    return '删除'
  }
  return undefined
}

function needsSpaceBefore(list: TextSegment[], index: number): boolean {
  if (index <= 0) {
    return false
  }
  const prev = list[index - 1]
  const cur = list[index]
  if (!prev || !cur) {
    return false
  }
  if (prev.kind === 'gap' || cur.kind === 'gap') {
    return false
  }
  return true
}
</script>

<template>
  <p v-if="!segments" class="edit-text">{{ text }}</p>
  <p v-else class="edit-text" aria-label="带操作标记的句子">
    <template v-for="(seg, i) in segments" :key="i">
      <span v-if="needsSpaceBefore(segments, i)"> </span>
      <span
          v-if="seg.kind === 'gap'"
          class="tok tok--gap"
          :title="opTitle('M')"
          aria-hidden="true"
      />
      <span
          v-else
          class="tok"
          :class="{
            'tok--del': seg.kind === 'del',
            'tok--ins': seg.kind === 'ins',
          }"
          :title="opTitle(seg.op)"
      >{{ seg.text }}</span>
    </template>
  </p>
</template>

<style scoped>
.edit-text {
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.9rem;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.tok {
  display: inline;
}

.tok--del {
  text-decoration: line-through;
  text-decoration-thickness: 1.5px;
  color: color-mix(in srgb, var(--kk-color-danger) 78%, var(--kk-color-text-muted));
  background: color-mix(in srgb, var(--kk-color-danger-bg) 88%, transparent);
  border-radius: 0.2em;
  padding: 0 0.12em;
}

.tok--ins {
  font-weight: 600;
  color: color-mix(in srgb, var(--kk-color-success, #1a7f4b) 55%, var(--kk-color-link));
  background: color-mix(in srgb, var(--kk-color-accent-bg, #e8f7ee) 70%, white);
  border-radius: 0.2em;
  padding: 0 0.12em;
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
}

.tok--gap {
  display: inline-block;
  width: 0.28em;
  height: 1em;
  margin: 0 0.08em;
  vertical-align: -0.1em;
  border-left: 2px solid color-mix(in srgb, var(--kk-color-accent) 65%, transparent);
  border-radius: 1px;
  opacity: 0.85;
}
</style>
