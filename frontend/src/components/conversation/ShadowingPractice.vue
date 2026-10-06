<script setup lang="ts">
import {Headset, Loading, Microphone, VideoPause} from '@element-plus/icons-vue'
import {computed, onBeforeUnmount, ref} from 'vue'

import {fetchShadowingAudio, scoreShadowing} from '@/api/shadowing'
import {useWavRecorder} from '@/composables/useWavRecorder'
import type {ShadowingScore} from '@/types/shadowing'
import {getErrorMessage} from '@/utils/error'

const MAX_RECORD_SECONDS = 15
const SLOW_RATE = 0.75

const props = defineProps<{
  analysisId: string
  sentenceId: string | number
  text: string
  ttsEnabled: boolean
}>()

const recorder = useWavRecorder(MAX_RECORD_SECONDS)

const playing = ref(false)
const slow = ref(false)
const scoring = ref(false)
const result = ref<ShadowingScore | null>(null)
const errorText = ref('')

let audioUrl: string | null = null
let audioEl: HTMLAudioElement | null = null
let ttsUnavailable = false

const hasSpeechSynthesis = typeof window !== 'undefined' && 'speechSynthesis' in window
const canPlayOriginal = computed(() => props.ttsEnabled || hasSpeechSynthesis)
const busy = computed(() => scoring.value)
const remaining = computed(() => Math.max(0, MAX_RECORD_SECONDS - recorder.elapsed.value))

async function togglePlay() {
  if (playing.value) {
    stopPlayback()
    return
  }
  errorText.value = ''
  playing.value = true
  const url = await loadAudioUrl()
  if (!playing.value) {
    return
  }
  if (url) {
    playUrl(url)
  } else {
    speak()
  }
}

async function loadAudioUrl(): Promise<string | null> {
  if (audioUrl || !props.ttsEnabled || ttsUnavailable) {
    return audioUrl
  }
  try {
    const {data} = await fetchShadowingAudio(props.analysisId, props.sentenceId)
    audioUrl = URL.createObjectURL(data)
  } catch {
    ttsUnavailable = true
  }
  return audioUrl
}

function playUrl(url: string) {
  audioEl ??= new Audio()
  audioEl.src = url
  audioEl.playbackRate = slow.value ? SLOW_RATE : 1
  audioEl.onended = () => {
    playing.value = false
  }
  audioEl.play().catch(() => {
    playing.value = false
    errorText.value = '原声播放失败'
  })
}

function speak() {
  if (!hasSpeechSynthesis) {
    playing.value = false
    errorText.value = '原声暂不可用'
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(props.text)
  utterance.lang = 'en-US'
  utterance.rate = slow.value ? SLOW_RATE : 1
  utterance.onend = () => {
    playing.value = false
  }
  utterance.onerror = () => {
    playing.value = false
  }
  window.speechSynthesis.speak(utterance)
}

function stopPlayback() {
  playing.value = false
  audioEl?.pause()
  if (hasSpeechSynthesis) {
    window.speechSynthesis.cancel()
  }
}

async function toggleRecord() {
  if (recorder.recording.value) {
    recorder.stop()
    return
  }
  stopPlayback()
  errorText.value = ''
  let wav: Blob
  try {
    wav = await recorder.record()
  } catch (error) {
    errorText.value = getErrorMessage(error, '录音失败')
    return
  }
  scoring.value = true
  try {
    const {data} = await scoreShadowing(props.analysisId, props.sentenceId, wav)
    result.value = data
  } catch (error) {
    errorText.value = getErrorMessage(error, '打分失败，请重试')
  } finally {
    scoring.value = false
  }
}

onBeforeUnmount(() => {
  stopPlayback()
  if (audioUrl) {
    URL.revokeObjectURL(audioUrl)
  }
})
</script>

<template>
  <section class="sh" aria-label="影子跟读">
    <div class="sh-toolbar">
      <button
          v-if="canPlayOriginal"
          type="button"
          class="sh-btn"
          :class="{ 'sh-btn--active': playing }"
          :disabled="recorder.recording.value"
          @click="togglePlay"
      >
        <el-icon><VideoPause v-if="playing"/><Headset v-else/></el-icon>
        {{ playing ? '停止' : '听原声' }}
      </button>
      <button
          v-if="canPlayOriginal"
          type="button"
          class="sh-btn sh-btn--chip"
          :class="{ 'sh-btn--active': slow }"
          :aria-pressed="slow"
          title="慢速播放"
          @click="slow = !slow"
      >
        0.75×
      </button>
      <button
          type="button"
          class="kk-btn-blue kk-btn-blue--compact sh-record"
          :class="{ 'sh-record--live': recorder.recording.value }"
          :disabled="busy"
          @click="toggleRecord"
      >
        <el-icon v-if="scoring" class="sh-spin"><Loading/></el-icon>
        <el-icon v-else><Microphone/></el-icon>
        <template v-if="scoring">打分中…</template>
        <template v-else-if="recorder.recording.value">读完点我 · {{ remaining }}s</template>
        <template v-else>{{ result ? '再读一遍' : '跟读打分' }}</template>
      </button>
      <span
          v-if="result && !recorder.recording.value"
          class="sh-score"
          :class="result.passed ? 'sh-score--pass' : 'sh-score--retry'"
      >
        {{ result.score }}<small>分</small>
      </span>
    </div>

    <p v-if="errorText" class="sh-error" role="alert">{{ errorText }}</p>

    <div v-if="result && !recorder.recording.value" class="sh-result">
      <p class="sh-words">
        <span
            v-for="(word, i) in result.words"
            :key="i"
            class="sh-word"
            :class="{ 'sh-word--miss': word.scored && !word.hit, 'sh-word--skip': !word.scored }"
        >{{ word.text }}</span>
      </p>
      <p class="sh-heard">
        听到：<span>{{ result.recognizedText || '（没有识别出内容）' }}</span>
      </p>
    </div>
  </section>
</template>

<style scoped>
.sh {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  padding-top: 0.45rem;
  border-top: 1px dashed var(--kk-color-border-subtle);
}

.sh-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.sh-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  margin: 0;
  padding: 0.22rem 0.6rem;
  border-radius: var(--kk-radius-pill);
  border: 1px solid var(--kk-glass-inner-border);
  background: var(--kk-glass-inner-bg);
  color: var(--kk-color-primary);
  font-family: inherit;
  font-size: 0.7rem;
  font-weight: 600;
  line-height: 1.3;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease;
}

.sh-btn:hover:not(:disabled) {
  background: var(--kk-glass-hover-bg);
  border-color: var(--kk-glass-hover-border);
}

.sh-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.sh-btn--chip {
  padding-inline: 0.45rem;
  font-family: var(--kk-font-mono);
  color: var(--kk-color-text-subtle);
}

.sh-btn--active {
  border-color: color-mix(in srgb, var(--kk-color-primary) 40%, transparent);
  background: color-mix(in srgb, var(--kk-color-primary) 10%, transparent);
  color: var(--kk-color-primary);
}

.sh-record--live {
  background: var(--kk-color-danger);
  animation: sh-pulse 1.4s ease-in-out infinite;
}

.sh-score {
  margin-left: auto;
  padding: 0.1rem 0.55rem;
  border-radius: var(--kk-radius-pill);
  font-family: var(--kk-font-display);
  font-size: 0.95rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.sh-score small {
  margin-left: 0.1rem;
  font-family: var(--kk-font-body);
  font-size: 0.65rem;
  font-weight: 600;
}

.sh-score--pass {
  color: var(--kk-color-success);
  background: var(--kk-color-success-bg);
}

.sh-score--retry {
  color: var(--kk-color-warn);
  background: var(--kk-color-warn-bg);
}

.sh-error {
  margin: 0;
  font-size: 0.74rem;
  color: var(--kk-color-danger);
}

.sh-result {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.sh-words {
  display: flex;
  flex-wrap: wrap;
  gap: 0.15rem 0.35rem;
  margin: 0;
  font-family: var(--kk-font-mono);
  font-size: 0.82rem;
  line-height: 1.5;
}

.sh-word {
  color: var(--kk-color-success);
}

.sh-word--miss {
  color: var(--kk-color-danger);
  text-decoration: underline wavy color-mix(in srgb, var(--kk-color-danger) 55%, transparent);
  text-underline-offset: 0.2em;
}

.sh-word--skip {
  color: var(--kk-color-text-subtle);
}

.sh-heard {
  margin: 0;
  font-size: 0.72rem;
  color: var(--kk-color-text-subtle);
}

.sh-heard span {
  font-family: var(--kk-font-mono);
  overflow-wrap: anywhere;
}

.sh-spin {
  animation: sh-rotate 1s linear infinite;
}

@keyframes sh-pulse {
  50% {
    opacity: 0.75;
  }
}

@keyframes sh-rotate {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sh-record--live,
  .sh-spin {
    animation: none;
  }
}
</style>
