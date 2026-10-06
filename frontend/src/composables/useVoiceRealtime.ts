import {ref, shallowRef} from 'vue'

import {
  buildVoiceRealtimeWsUrl,
  fetchVoiceRealtimeConfig,
  type VoiceRealtimeConfig,
} from '@/api/voiceRealtime'
import {AUTH_TOKEN_KEY} from '@/constants/auth'
import {getErrorMessage} from '@/utils/error'

export type VoiceSessionStatus =
  | 'idle'
  | 'checking'
  | 'connecting'
  | 'live'
  | 'error'
  | 'ended'

export interface VoiceCaptionLine {
  role: 'user' | 'assistant' | 'system'
  text: string
}

const INPUT_RATE = 16_000
const OUTPUT_RATE = 24_000
const CHUNK_MS = 20
const SAMPLES_PER_CHUNK = (INPUT_RATE * CHUNK_MS) / 1000 // 320

function floatTo16BitPcm(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length)
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i]!))
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return out
}

function downsampleTo16k(input: Float32Array, inputRate: number): Float32Array {
  if (inputRate === INPUT_RATE) {
    return input
  }
  const ratio = inputRate / INPUT_RATE
  const newLen = Math.floor(input.length / ratio)
  const result = new Float32Array(newLen)
  for (let i = 0; i < newLen; i++) {
    const start = Math.floor(i * ratio)
    result[i] = input[start] ?? 0
  }
  return result
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

function base64ToInt16(base64: string): Int16Array {
  const binary = atob(base64)
  const len = binary.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.byteLength / 2))
}

/**
 * 豆包全双工实时语音客户端：浏览器采麦/播音，经后端 WS 代理转发 JSON 事件。
 */
export function useVoiceRealtime() {
  const status = ref<VoiceSessionStatus>('idle')
  const statusDetail = ref('')
  const config = shallowRef<VoiceRealtimeConfig | null>(null)
  const captions = ref<VoiceCaptionLine[]>([])
  const userPartial = ref('')
  const assistantPartial = ref('')
  const muted = ref(false)

  let socket: WebSocket | null = null
  let mediaStream: MediaStream | null = null
  let captureCtx: AudioContext | null = null
  let playbackCtx: AudioContext | null = null
  let processor: ScriptProcessorNode | null = null
  let pcmQueue: number[] = []
  let sendTimer: ReturnType<typeof setInterval> | null = null
  let nextPlayTime = 0
  let closing = false

  function pushCaption(role: VoiceCaptionLine['role'], text: string) {
    const trimmed = text.trim()
    if (!trimmed) {
      return
    }
    const last = captions.value[captions.value.length - 1]
    if (last && last.role === role) {
      last.text = `${last.text} ${trimmed}`.trim()
      captions.value = [...captions.value]
      return
    }
    captions.value = [...captions.value, {role, text: trimmed}]
  }

  async function loadConfig() {
    status.value = 'checking'
    statusDetail.value = '检查实时语音配置…'
    try {
      const {data} = await fetchVoiceRealtimeConfig()
      config.value = data
      if (!data.configured) {
        status.value = 'error'
        statusDetail.value = data.message || '未配置豆包语音实时对话密钥'
        return false
      }
      status.value = 'idle'
      statusDetail.value = '已就绪，可开始对话'
      return true
    } catch (error) {
      status.value = 'error'
      statusDetail.value = getErrorMessage(error, '读取实时语音配置失败')
      return false
    }
  }

  function sendEvent(payload: Record<string, unknown>) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return
    }
    socket.send(JSON.stringify(payload))
  }

  function schedulePcmPlayback(samples: Int16Array) {
    if (!playbackCtx || samples.length === 0) {
      return
    }
    const buffer = playbackCtx.createBuffer(1, samples.length, OUTPUT_RATE)
    const channel = buffer.getChannelData(0)
    for (let i = 0; i < samples.length; i++) {
      channel[i] = (samples[i] ?? 0) / 0x8000
    }
    const source = playbackCtx.createBufferSource()
    source.buffer = buffer
    source.connect(playbackCtx.destination)
    const now = playbackCtx.currentTime
    if (nextPlayTime < now) {
      nextPlayTime = now
    }
    source.start(nextPlayTime)
    nextPlayTime += buffer.duration
  }

  /**
   * 解析上游/代理 error 帧。常见形状：
   * - `{ type, message, code? }`（本站代理）
   * - `{ type, error: { message, code? }, code? }`（openspeech 嵌套）
   */
  function formatRealtimeError(event: Record<string, unknown>): string {
    const nested =
      event.error && typeof event.error === 'object'
        ? (event.error as Record<string, unknown>)
        : null
    const messageCandidates = [event.message, nested?.message]
    const message = messageCandidates.find((v): v is string => typeof v === 'string' && v.trim().length > 0)
    const codeCandidates = [event.code, nested?.code]
    const code = codeCandidates.find((v) => v !== undefined && v !== null && String(v).length > 0)
    if (message && code !== undefined) {
      return `${message}（code=${String(code)}）`
    }
    if (message) {
      return message
    }
    if (code !== undefined) {
      return `实时语音错误 code=${String(code)}`
    }
    try {
      const snippet = JSON.stringify(event)
      return `实时语音服务返回错误：${snippet.length > 240 ? `${snippet.slice(0, 240)}…` : snippet}`
    } catch {
      return '实时语音服务返回错误'
    }
  }

  function handleServerEvent(raw: string) {
    let event: Record<string, unknown>
    try {
      event = JSON.parse(raw) as Record<string, unknown>
    } catch {
      return
    }
    const type = String(event.type ?? '')
    if (type === 'error') {
      console.warn('[voice-realtime] error event', event)
      status.value = 'error'
      statusDetail.value = formatRealtimeError(event)
      return
    }
    if (type === 'session.created') {
      status.value = 'live'
      statusDetail.value = '会话已建立 · 全双工中'
      // MVP：不在 session.created 后发 speech_text_buffer.commit（可选问候且 schema 易错）
      return
    }
    if (type === 'conversation.item.input_audio_transcription.delta') {
      const delta = String(event.delta ?? event.text ?? '')
      userPartial.value += delta
      return
    }
    if (type === 'conversation.item.input_audio_transcription.completed') {
      const text = String(event.transcript ?? event.text ?? userPartial.value)
      pushCaption('user', text)
      userPartial.value = ''
      return
    }
    if (type === 'response.output_text.delta') {
      const delta = String(event.delta ?? event.text ?? '')
      assistantPartial.value += delta
      return
    }
    if (type === 'response.output_text.done') {
      pushCaption('assistant', String(event.text ?? assistantPartial.value))
      assistantPartial.value = ''
      return
    }
    if (type === 'response.output_audio.delta') {
      const audio = String(event.delta ?? event.audio ?? '')
      if (audio) {
        schedulePcmPlayback(base64ToInt16(audio))
      }
      return
    }
    if (type === 'session.closed') {
      status.value = 'ended'
      statusDetail.value = '会话已结束'
    }
  }

  function flushAudioChunk() {
    if (!socket || socket.readyState !== WebSocket.OPEN || muted.value) {
      return
    }
    if (pcmQueue.length < SAMPLES_PER_CHUNK) {
      return
    }
    const chunk = new Int16Array(SAMPLES_PER_CHUNK)
    for (let i = 0; i < SAMPLES_PER_CHUNK; i++) {
      chunk[i] = pcmQueue.shift() ?? 0
    }
    const bytes = new Uint8Array(chunk.buffer)
    sendEvent({
      type: 'input_audio_buffer.append',
      event_id: `a_${Date.now()}`,
      audio: bytesToBase64(bytes),
    })
  }

  async function startMic() {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
      },
      video: false,
    })
    captureCtx = new AudioContext()
    playbackCtx = new AudioContext({sampleRate: OUTPUT_RATE})
    const source = captureCtx.createMediaStreamSource(mediaStream)
    // ScriptProcessor 已废弃但兼容性最好；MVP 足够
    processor = captureCtx.createScriptProcessor(4096, 1, 1)
    processor.onaudioprocess = (ev) => {
      if (muted.value || closing) {
        return
      }
      const input = ev.inputBuffer.getChannelData(0)
      const down = downsampleTo16k(input, captureCtx!.sampleRate)
      const pcm = floatTo16BitPcm(down)
      for (let i = 0; i < pcm.length; i++) {
        pcmQueue.push(pcm[i]!)
      }
    }
    source.connect(processor)
    processor.connect(captureCtx.destination)
    sendTimer = setInterval(flushAudioChunk, CHUNK_MS)
  }

  async function start(instructions?: string) {
    closing = false
    captions.value = []
    userPartial.value = ''
    assistantPartial.value = ''
    pcmQueue = []

    const ok = config.value?.configured ? true : await loadConfig()
    if (!ok || !config.value?.configured) {
      return
    }

    const token = localStorage.getItem(AUTH_TOKEN_KEY)
    if (!token) {
      status.value = 'error'
      statusDetail.value = '请先登录后再开始实时口语陪练'
      return
    }

    status.value = 'connecting'
    statusDetail.value = '请求麦克风权限并连接代理…'

    try {
      await startMic()
    } catch {
      status.value = 'error'
      statusDetail.value = '无法获取麦克风权限，请在浏览器设置中允许后重试'
      return
    }

    const wsUrl = buildVoiceRealtimeWsUrl(config.value.wsPath, token)
    socket = new WebSocket(wsUrl)
    socket.onopen = () => {
      sendEvent({
        type: 'session.create',
        session: {
          model: config.value!.model || '1.2.6.1',
          instructions: instructions?.trim() || config.value!.defaultInstructions,
          audio: {
            input: {format: {type: 'pcm', rate: INPUT_RATE}},
            output: {
              format: {type: 'pcm_s16le', rate: OUTPUT_RATE},
              voice: config.value!.voice,
              speed: 0,
              loudness: 0,
            },
          },
        },
        extension: {asr: {}, tts: {}, dialog: {}},
      })
      statusDetail.value = '等待 session.created…'
    }
    socket.onmessage = (ev) => {
      if (typeof ev.data === 'string') {
        handleServerEvent(ev.data)
      }
    }
    socket.onerror = () => {
      status.value = 'error'
      statusDetail.value = 'WebSocket 连接异常'
    }
    socket.onclose = () => {
      if (!closing && status.value === 'live') {
        status.value = 'ended'
        statusDetail.value = '连接已关闭'
      }
      void cleanupMedia()
    }
  }

  async function cleanupMedia() {
    if (sendTimer) {
      clearInterval(sendTimer)
      sendTimer = null
    }
    if (processor) {
      processor.disconnect()
      processor.onaudioprocess = null
      processor = null
    }
    if (captureCtx) {
      await captureCtx.close().catch(() => undefined)
      captureCtx = null
    }
    if (playbackCtx) {
      await playbackCtx.close().catch(() => undefined)
      playbackCtx = null
    }
    if (mediaStream) {
      mediaStream.getTracks().forEach((t) => t.stop())
      mediaStream = null
    }
    pcmQueue = []
    nextPlayTime = 0
  }

  async function stop() {
    closing = true
    if (socket && socket.readyState === WebSocket.OPEN) {
      sendEvent({type: 'session.close', event_id: `close_${Date.now()}`})
      // 稍等服务端 ack；超时仍关闭
      setTimeout(() => {
        socket?.close()
      }, 800)
    } else {
      socket?.close()
    }
    await cleanupMedia()
    status.value = 'ended'
    statusDetail.value = '已结束'
  }

  function interrupt() {
    sendEvent({type: 'response.cancel', event_id: `cancel_${Date.now()}`})
    nextPlayTime = playbackCtx?.currentTime ?? 0
  }

  function toggleMute() {
    muted.value = !muted.value
    if (muted.value) {
      sendEvent({type: 'input_audio_mute.commit', event_id: `mute_${Date.now()}`})
    } else {
      sendEvent({type: 'input_audio_unmute.commit', event_id: `unmute_${Date.now()}`})
    }
  }

  return {
    status,
    statusDetail,
    config,
    captions,
    userPartial,
    assistantPartial,
    muted,
    loadConfig,
    start,
    stop,
    interrupt,
    toggleMute,
  }
}
