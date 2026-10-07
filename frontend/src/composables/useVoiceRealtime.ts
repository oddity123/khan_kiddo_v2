import {ref, shallowRef} from 'vue'

import {
  buildVoiceRealtimeWsUrl,
  fetchVoiceRealtimeConfig,
  type VoiceRealtimeConfig,
} from '@/api/voiceRealtime'
import {AUTH_TOKEN_KEY} from '@/constants/auth'
import {getErrorMessage} from '@/utils/error'
import {
  base64ToInt16,
  bytesToBase64,
  downsampleTo16k,
  floatTo16BitPcm,
} from '@/utils/voiceRealtimeAudio'
import {formatRealtimeError} from '@/utils/voiceRealtimeError'
import {mergeStreamingText, pickAsrStreamingPreview} from '@/utils/voiceRealtimeText'

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

/** 与后端 VoiceRealtimeProperties 默认值同源；仅在 config 缺字段时兜底 */
const DEFAULT_INPUT_RATE = 16_000
const DEFAULT_OUTPUT_RATE = 24_000
const DEFAULT_CHUNK_MS = 20

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
  /** 已排程、尚未结束的 TTS BufferSource；打断时须全部 stop */
  let activeSources: AudioBufferSourceNode[] = []
  /** 正在播 assistant 音频（含已排程未响完） */
  let assistantAudioPlaying = false
  let closing = false
  /** 仅在收到后端 proxy.ready 后允许 session.create / 麦流上行 */
  let proxyReady = false
  let pendingInstructions: string | undefined

  function inputRate(): number {
    return config.value?.inputSampleRate || DEFAULT_INPUT_RATE
  }

  function outputRate(): number {
    return config.value?.outputSampleRate || DEFAULT_OUTPUT_RATE
  }

  function chunkMs(): number {
    return config.value?.chunkMs || DEFAULT_CHUNK_MS
  }

  function samplesPerChunk(): number {
    return (inputRate() * chunkMs()) / 1000
  }

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

  function markSourceEnded(source: AudioBufferSourceNode) {
    activeSources = activeSources.filter((s) => s !== source)
    if (activeSources.length === 0) {
      assistantAudioPlaying = false
    }
  }

  function stopAllPlayback() {
    for (const source of activeSources) {
      try {
        source.onended = null
        source.stop()
      } catch {
        // already stopped
      }
    }
    activeSources = []
    assistantAudioPlaying = false
    nextPlayTime = playbackCtx?.currentTime ?? 0
  }

  function schedulePcmPlayback(samples: Int16Array) {
    if (!playbackCtx || samples.length === 0) {
      return
    }
    const buffer = playbackCtx.createBuffer(1, samples.length, outputRate())
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
    activeSources.push(source)
    assistantAudioPlaying = true
    source.onended = () => markSourceEnded(source)
    source.start(nextPlayTime)
    nextPlayTime += buffer.duration
  }

  function handleServerEvent(raw: string) {
    let event: Record<string, unknown>
    try {
      event = JSON.parse(raw) as Record<string, unknown>
    } catch {
      return
    }
    const type = String(event.type ?? '')
    if (type === 'proxy.ready') {
      onProxyReady()
      return
    }
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
    // 用户侧 ASR 开始：若正在播 assistant，自动 barge-in（与手动按钮共用 interrupt）
    if (type === 'conversation.item.input_audio_transcription.started') {
      if (assistantAudioPlaying || assistantPartial.value) {
        interrupt()
      }
      return
    }
    if (type === 'conversation.item.input_audio_transcription.delta') {
      // ASR 预览一律覆盖赋值：豆包侧 delta/text 多为累计快照；前缀合并会在改写时叠字
      const latest = pickAsrStreamingPreview(event)
      if (latest) {
        userPartial.value = latest
      }
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
      // assistant 文本流按增量/快照启发式合并（ASR 不用此路径）
      assistantPartial.value = mergeStreamingText(assistantPartial.value, delta)
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
    if (type === 'response.canceled') {
      stopAllPlayback()
      if (assistantPartial.value.trim()) {
        pushCaption('assistant', assistantPartial.value)
        assistantPartial.value = ''
      }
      return
    }
    if (type === 'session.closed') {
      status.value = 'ended'
      statusDetail.value = '会话已结束'
    }
  }

  function sendSessionCreate() {
    const model = config.value?.model?.trim()
    if (!model) {
      status.value = 'error'
      statusDetail.value = '服务端未返回实时语音 model，无法创建会话'
      return
    }
    sendEvent({
      type: 'session.create',
      session: {
        model,
        instructions: pendingInstructions?.trim() || config.value!.defaultInstructions,
        audio: {
          input: {format: {type: 'pcm', rate: inputRate()}},
          output: {
            format: {type: 'pcm_s16le', rate: outputRate()},
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

  function onProxyReady() {
    if (proxyReady || closing) {
      return
    }
    proxyReady = true
    statusDetail.value = '上游已就绪，创建会话…'
    sendSessionCreate()
    if (!sendTimer) {
      sendTimer = setInterval(flushAudioChunk, chunkMs())
    }
  }

  function flushAudioChunk() {
    if (!proxyReady || !socket || socket.readyState !== WebSocket.OPEN || muted.value) {
      return
    }
    const n = samplesPerChunk()
    if (pcmQueue.length < n) {
      return
    }
    const chunk = new Int16Array(n)
    for (let i = 0; i < n; i++) {
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
    playbackCtx = new AudioContext({sampleRate: outputRate()})
    const source = captureCtx.createMediaStreamSource(mediaStream)
    // ScriptProcessor 已废弃但兼容性最好；MVP 足够
    processor = captureCtx.createScriptProcessor(4096, 1, 1)
    const targetInputRate = inputRate()
    processor.onaudioprocess = (ev) => {
      if (muted.value || closing) {
        return
      }
      const input = ev.inputBuffer.getChannelData(0)
      const down = downsampleTo16k(input, captureCtx!.sampleRate, targetInputRate)
      const pcm = floatTo16BitPcm(down)
      for (let i = 0; i < pcm.length; i++) {
        pcmQueue.push(pcm[i]!)
      }
    }
    source.connect(processor)
    processor.connect(captureCtx.destination)
    // 麦流上行延后到 proxy.ready，避免 append 早于上游 attach
  }

  async function start(instructions?: string) {
    closing = false
    proxyReady = false
    pendingInstructions = instructions
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
      // 双保险：等后端 proxy.ready 再 session.create；服务端仍会缓冲早到帧
      statusDetail.value = '等待代理上游就绪（proxy.ready）…'
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
      proxyReady = false
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
    stopAllPlayback()
  }

  async function stop() {
    closing = true
    proxyReady = false
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
    stopAllPlayback()
    if (assistantPartial.value.trim()) {
      pushCaption('assistant', assistantPartial.value)
      assistantPartial.value = ''
    }
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
