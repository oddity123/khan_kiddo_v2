import type {ConnectionStatusResponse, ImportShareResult, SettingsResponse} from '../shared/protocol'
import {PRIVACY_CONSENT_STORAGE_KEY} from '../shared/constants'
import {loadCredential, loadPendingConnect} from '../shared/credentialStore'
import {mountConnectionProbe} from '../connection/popup'
import {mountDailyPractice} from '../connection/dailyPracticePopup'

const dailyPractice = mountDailyPractice()

if (import.meta.env.MODE !== 'production') {
  mountConnectionProbe()
} else {
  document.querySelector<HTMLDetailsElement>('#shareSection')!.open = true
}

const originRow = document.getElementById('originRow') as HTMLDetailsElement
const webOriginInput = document.getElementById('webOrigin') as HTMLInputElement
const shareUrlInput = document.getElementById('shareUrl') as HTMLTextAreaElement
const saveBtn = document.getElementById('saveBtn') as HTMLButtonElement
const importBtn = document.getElementById('importBtn') as HTMLButtonElement
const statusEl = document.getElementById('status') as HTMLDivElement
const privacyConsentInput = document.getElementById('privacyConsent') as HTMLInputElement
const accountSection = document.getElementById('accountSection') as HTMLElement
const connectBtn = document.getElementById('connectBtn') as HTMLButtonElement
const disconnectBtn = document.getElementById('disconnectBtn') as HTMLButtonElement
const accountStatusEl = document.getElementById('accountStatus') as HTMLElement
const accountStatusHeroEl = document.getElementById('accountStatusHero') as HTMLParagraphElement
const accountHintEl = document.getElementById('accountHint') as HTMLParagraphElement

let allowOriginOverride = false

function setHeroStatus(text: string, kind: '' | 'ok' | 'error' = ''): void {
  accountStatusHeroEl.textContent = text
  accountStatusHeroEl.className = kind ? `status ${kind}` : 'status'
}

function applyConnectionUi(status: ConnectionStatusResponse): void {
  const connected = Boolean(status.connected) && !status.connecting

  if (status.connecting) {
    accountSection.classList.remove('hidden')
    accountStatusEl.textContent = ''
    disconnectBtn.classList.add('hidden')
    setHeroStatus('请在打开的页面完成授权…')
    accountHintEl.textContent = '连接后可将 ChatGPT 对话提交到 Khan Kiddo 分析。'
    connectBtn.classList.remove('hidden')
    connectBtn.disabled = true
    dailyPractice?.hide()
    return
  }

  connectBtn.disabled = false
  if (connected) {
    accountSection.classList.add('hidden')
    const who = status.username ? status.username : '已连接'
    accountStatusEl.textContent = who
    accountStatusEl.className = status.error ? 'chip error' : 'chip'
    accountStatusEl.title = status.error ? `${who}（${status.error}）` : who
    setHeroStatus('')
    connectBtn.classList.add('hidden')
    disconnectBtn.classList.remove('hidden')
    dailyPractice?.show()
    return
  }

  accountSection.classList.remove('hidden')
  accountStatusEl.textContent = ''
  accountStatusEl.removeAttribute('title')
  connectBtn.classList.remove('hidden')
  disconnectBtn.classList.add('hidden')
  accountHintEl.textContent = '连接后可将 ChatGPT 对话提交到 Khan Kiddo 分析。'
  dailyPractice?.hide()
  if (status.error) {
    setHeroStatus(status.error, 'error')
  } else {
    setHeroStatus('')
  }
}

async function refreshConnection(): Promise<void> {
  const status = (await chrome.runtime.sendMessage({
    type: 'GET_CONNECTION',
  })) as ConnectionStatusResponse
  applyConnectionUi(status)
}

/** 打开弹窗时先用本地凭证/待兑换状态占位，避免先闪「连接账号」再等 /me。 */
async function paintLocalConnectionHint(): Promise<void> {
  const pending = await loadPendingConnect()
  if (pending) {
    applyConnectionUi({type: 'CONNECTION_STATUS', connected: false, connecting: true})
    return
  }
  const cred = await loadCredential()
  if (cred) {
    applyConnectionUi({
      type: 'CONNECTION_STATUS',
      connected: true,
      username: cred.username,
      expiresAt: cred.expiresAt,
    })
    return
  }
  applyConnectionUi({type: 'CONNECTION_STATUS', connected: false})
}

async function loadPrivacyConsent(): Promise<void> {
  const stored = await chrome.storage.local.get(PRIVACY_CONSENT_STORAGE_KEY)
  privacyConsentInput.checked = stored[PRIVACY_CONSENT_STORAGE_KEY] === true
}

privacyConsentInput.addEventListener('change', () => {
  void chrome.storage.local.set({
    [PRIVACY_CONSENT_STORAGE_KEY]: privacyConsentInput.checked,
  })
})

function setStatus(text: string, kind: '' | 'ok' | 'error' = ''): void {
  statusEl.textContent = text
  statusEl.className = kind ? `status ${kind}` : 'status'
}

function applySettingsUi(settings: SettingsResponse): void {
  allowOriginOverride = settings.allowOriginOverride
  webOriginInput.value = settings.webOrigin
  originRow.classList.toggle('hidden', !allowOriginOverride)
  saveBtn.classList.toggle('hidden', !allowOriginOverride)
}

async function loadSettings(): Promise<void> {
  const response = (await chrome.runtime.sendMessage({type: 'GET_SETTINGS'})) as SettingsResponse
  applySettingsUi(response)
}

saveBtn.addEventListener('click', () => {
  void (async () => {
    if (!allowOriginOverride) {
      return
    }
    saveBtn.disabled = true
    try {
      const response = (await chrome.runtime.sendMessage({
        type: 'SAVE_SETTINGS',
        webOrigin: webOriginInput.value,
      })) as SettingsResponse
      applySettingsUi(response)
      setStatus('设置已保存', 'ok')
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '保存失败', 'error')
    } finally {
      saveBtn.disabled = false
    }
  })()
})

importBtn.addEventListener('click', () => {
  void (async () => {
    if (!privacyConsentInput.checked) {
      setStatus('请先阅读并同意数据使用说明', 'error')
      return
    }
    const shareUrl = shareUrlInput.value.trim()
    if (!shareUrl) {
      setStatus('请粘贴 ChatGPT 分享链接', 'error')
      return
    }
    importBtn.disabled = true
    setStatus('正在导入…')
    try {
      if (allowOriginOverride) {
        await chrome.runtime.sendMessage({
          type: 'SAVE_SETTINGS',
          webOrigin: webOriginInput.value,
        })
      }
      const result = (await chrome.runtime.sendMessage({
        type: 'IMPORT_SHARE',
        shareUrl,
      })) as ImportShareResult
      if (!result.ok) {
        setStatus(result.error || '导入失败', 'error')
        return
      }
      setStatus(`已导入 ${result.payload?.messageCount ?? 0} 条，已打开分析页`, 'ok')
    } catch (error) {
      setStatus(error instanceof Error ? error.message : '导入失败', 'error')
    } finally {
      importBtn.disabled = false
    }
  })()
})

connectBtn.addEventListener('click', () => {
  void (async () => {
    connectBtn.disabled = true
    setHeroStatus('正在打开授权页…')
    try {
      if (allowOriginOverride) {
        await chrome.runtime.sendMessage({
          type: 'SAVE_SETTINGS',
          webOrigin: webOriginInput.value,
        })
      }
      const status = (await chrome.runtime.sendMessage({
        type: 'START_CONNECT',
      })) as ConnectionStatusResponse
      applyConnectionUi(status)
    } catch (error) {
      setHeroStatus(error instanceof Error ? error.message : '连接失败', 'error')
      connectBtn.disabled = false
    }
  })()
})

disconnectBtn.addEventListener('click', () => {
  void (async () => {
    disconnectBtn.disabled = true
    try {
      const status = (await chrome.runtime.sendMessage({
        type: 'REVOKE_CONNECTION',
      })) as ConnectionStatusResponse
      applyConnectionUi(status)
    } catch (error) {
      accountStatusEl.textContent = error instanceof Error ? error.message : '断开失败'
      accountStatusEl.className = 'chip error'
    } finally {
      disconnectBtn.disabled = false
    }
  })()
})

void (async () => {
  await Promise.all([loadSettings(), loadPrivacyConsent(), paintLocalConnectionHint()])
  await refreshConnection()
})()
