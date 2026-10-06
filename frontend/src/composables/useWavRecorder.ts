import {onBeforeUnmount, ref} from 'vue'

import {encodeWavPcm16, isSilent, SHADOWING_SAMPLE_RATE} from '@/utils/wavEncoder'

const MIN_SECONDS = 0.4

/**
 * 麦克风录音 → 16kHz 单声道 PCM16 WAV。
 * `record()` 在 `stop()` 或到达 `maxSeconds` 时 resolve；`cancel()` 丢弃本次录音并 reject。
 */
export function useWavRecorder(maxSeconds = 15) {
  const recording = ref(false)
  const elapsed = ref(0)

  let stream: MediaStream | null = null
  let recorder: MediaRecorder | null = null
  let tickTimer: ReturnType<typeof setInterval> | undefined
  let stopTimer: ReturnType<typeof setTimeout> | undefined
  let cancelled = false

  async function record(): Promise<Blob> {
    if (recording.value) {
      throw new Error('正在录音')
    }
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      throw new Error(window.isSecureContext ? '当前浏览器不支持录音' : '需要在 HTTPS 页面下才能录音')
    }
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {channelCount: 1, echoCancellation: true, noiseSuppression: true},
      })
    } catch (error) {
      throw new Error(microphoneErrorMessage(error))
    }

    cancelled = false
    const chunks: Blob[] = []
    const startedAt = Date.now()
    const activeRecorder = new MediaRecorder(stream)
    recorder = activeRecorder
    recording.value = true
    elapsed.value = 0

    const stopped = new Promise<void>((resolve) => {
      activeRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunks.push(event.data)
        }
      }
      activeRecorder.onstop = () => resolve()
    })
    activeRecorder.start()
    tickTimer = setInterval(() => {
      elapsed.value = Math.floor((Date.now() - startedAt) / 1000)
    }, 250)
    stopTimer = setTimeout(stop, maxSeconds * 1000)

    await stopped
    cleanup()
    if (cancelled) {
      throw new Error('录音已取消')
    }
    return toWav(new Blob(chunks, {type: activeRecorder.mimeType}))
  }

  function stop() {
    if (recorder?.state === 'recording') {
      recorder.stop()
    }
  }

  function cancel() {
    cancelled = true
    stop()
    cleanup()
  }

  function cleanup() {
    clearInterval(tickTimer)
    clearTimeout(stopTimer)
    stream?.getTracks().forEach((track) => track.stop())
    stream = null
    recorder = null
    recording.value = false
  }

  onBeforeUnmount(cancel)

  return {recording, elapsed, record, stop, cancel}
}

async function toWav(recorded: Blob): Promise<Blob> {
  const context = new AudioContext()
  let decoded: AudioBuffer
  try {
    decoded = await context.decodeAudioData(await recorded.arrayBuffer())
  } catch {
    throw new Error('录音解析失败，请重试')
  } finally {
    void context.close()
  }
  if (decoded.duration < MIN_SECONDS) {
    throw new Error('录音太短，请读完整句')
  }

  const offline = new OfflineAudioContext(
      1,
      Math.ceil(decoded.duration * SHADOWING_SAMPLE_RATE),
      SHADOWING_SAMPLE_RATE,
  )
  const source = offline.createBufferSource()
  source.buffer = decoded
  source.connect(offline.destination)
  source.start()
  const samples = (await offline.startRendering()).getChannelData(0)
  if (isSilent(samples)) {
    throw new Error('没有录到声音，请检查麦克风')
  }
  return new Blob([encodeWavPcm16(samples)], {type: 'audio/wav'})
}

function microphoneErrorMessage(error: unknown): string {
  const name = error instanceof DOMException ? error.name : ''
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return '麦克风权限被拒绝，请在浏览器地址栏允许麦克风'
  }
  if (name === 'NotFoundError' || name === 'OverconstrainedError') {
    return '没有检测到麦克风'
  }
  if (name === 'NotReadableError') {
    return '麦克风被其它程序占用'
  }
  return '无法打开麦克风'
}
