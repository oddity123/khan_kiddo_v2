import type {ImportShareResult, SettingsResponse} from '../shared/protocol'
import {PRIVACY_CONSENT_STORAGE_KEY} from '../shared/constants'

const originRow = document.getElementById('originRow') as HTMLDivElement
const webOriginInput = document.getElementById('webOrigin') as HTMLInputElement
const shareUrlInput = document.getElementById('shareUrl') as HTMLTextAreaElement
const saveBtn = document.getElementById('saveBtn') as HTMLButtonElement
const importBtn = document.getElementById('importBtn') as HTMLButtonElement
const statusEl = document.getElementById('status') as HTMLDivElement
const privacyConsentInput = document.getElementById('privacyConsent') as HTMLInputElement

let allowOriginOverride = false

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

void Promise.all([loadSettings(), loadPrivacyConsent()])
