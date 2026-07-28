import {
  ALLOW_WEB_ORIGIN_OVERRIDE,
  ANALYZE_IMPORT_SESSION_KEY,
  DEFAULT_WEB_ORIGIN,
  WEB_ORIGIN_STORAGE_KEY,
} from '../shared/constants'
import {fetchChatGptShare, formatConversationContent} from '../shared/chatgptShare'
import type {
  ExtensionRequest,
  ImportConversationPayload,
  ImportShareResult,
  SettingsResponse,
} from '../shared/protocol'
import {parseChatGptShareId} from '../shared/shareUrl'

async function getWebOrigin(): Promise<string> {
  if (!ALLOW_WEB_ORIGIN_OVERRIDE) {
    return DEFAULT_WEB_ORIGIN
  }
  const stored = await chrome.storage.local.get(WEB_ORIGIN_STORAGE_KEY)
  const value = stored[WEB_ORIGIN_STORAGE_KEY]
  if (typeof value === 'string' && value.trim()) {
    return value.trim().replace(/\/+$/, '')
  }
  return DEFAULT_WEB_ORIGIN
}

async function setWebOrigin(webOrigin: string): Promise<void> {
  if (!ALLOW_WEB_ORIGIN_OVERRIDE) {
    throw new Error('正式版站点地址已固定，无法修改')
  }
  const normalized = webOrigin.trim().replace(/\/+$/, '')
  new URL(normalized)
  await chrome.storage.local.set({[WEB_ORIGIN_STORAGE_KEY]: normalized})
}

function settingsPayload(webOrigin: string): SettingsResponse {
  return {
    type: 'SETTINGS',
    webOrigin,
    allowOriginOverride: ALLOW_WEB_ORIGIN_OVERRIDE,
  }
}

async function ensureHostPermission(origin: string): Promise<void> {
  const permissionUrl = `${new URL(origin).origin}/*`
  const has = await chrome.permissions.contains({origins: [permissionUrl]})
  if (has) {
    return
  }
  try {
    const granted = await chrome.permissions.request({origins: [permissionUrl]})
    if (!granted) {
      throw new Error(`未授予站点权限：${permissionUrl}`)
    }
  } catch {
    throw new Error(`请先打开扩展弹窗，保存站点地址并允许访问：${permissionUrl}`)
  }
}

function buildAnalyzeUrl(webOrigin: string): string {
  return `${webOrigin}/conversation/analyze`
}

async function injectImportPayload(tabId: number, payload: ImportConversationPayload): Promise<void> {
  const key = ANALYZE_IMPORT_SESSION_KEY
  const raw = JSON.stringify(payload)
  await chrome.scripting.executeScript({
    target: {tabId},
    func: (storageKey: string, json: string) => {
      sessionStorage.setItem(storageKey, json)
      window.dispatchEvent(new CustomEvent('kk-extension-import', {detail: storageKey}))
    },
    args: [key, raw],
  })
}

function waitForTabComplete(tabId: number, timeoutMs = 30000): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      chrome.tabs.onUpdated.removeListener(listener)
      reject(new Error('打开分析页超时'))
    }, timeoutMs)

    const listener = (updatedTabId: number, info: chrome.tabs.TabChangeInfo) => {
      if (updatedTabId !== tabId) {
        return
      }
      if (info.status === 'complete') {
        clearTimeout(timer)
        chrome.tabs.onUpdated.removeListener(listener)
        resolve()
      }
    }
    chrome.tabs.onUpdated.addListener(listener)

    chrome.tabs.get(tabId).then((tab) => {
      if (tab.status === 'complete') {
        clearTimeout(timer)
        chrome.tabs.onUpdated.removeListener(listener)
        resolve()
      }
    }).catch(() => {
      // ignore; wait for onUpdated
    })
  })
}

async function importShareToAnalyzePage(shareUrl: string): Promise<ImportConversationPayload> {
  const shareId = parseChatGptShareId(shareUrl)
  if (!shareId) {
    throw new Error('不是有效的 ChatGPT 分享链接')
  }

  const extracted = await fetchChatGptShare(shareId)
  const conversationContent = formatConversationContent(extracted.messages)
  const payload: ImportConversationPayload = {
    source: 'chatgpt_share',
    shareId: extracted.shareId,
    shareUrl,
    title: extracted.title,
    conversationContent,
    messageCount: extracted.messages.length,
    importedAt: new Date().toISOString(),
  }

  const webOrigin = await getWebOrigin()
  await ensureHostPermission(webOrigin)

  const analyzeUrl = `${buildAnalyzeUrl(webOrigin)}?kkExtImport=1`
  const tab = await chrome.tabs.create({url: analyzeUrl, active: true})
  if (tab.id == null) {
    throw new Error('无法打开分析页标签')
  }

  await waitForTabComplete(tab.id)
  await new Promise((r) => setTimeout(r, 500))
  try {
    await injectImportPayload(tab.id, payload)
  } catch {
    await new Promise((r) => setTimeout(r, 1000))
    await injectImportPayload(tab.id, payload)
  }

  return payload
}

chrome.runtime.onMessage.addListener((message: ExtensionRequest, _sender, sendResponse) => {
  const respond = async () => {
    if (message.type === 'GET_SETTINGS') {
      const webOrigin = await getWebOrigin()
      sendResponse(settingsPayload(webOrigin))
      return
    }
    if (message.type === 'SAVE_SETTINGS') {
      await setWebOrigin(message.webOrigin)
      const webOrigin = await getWebOrigin()
      sendResponse(settingsPayload(webOrigin))
      return
    }
    if (message.type === 'IMPORT_SHARE') {
      try {
        const payload = await importShareToAnalyzePage(message.shareUrl)
        const result: ImportShareResult = {type: 'IMPORT_SHARE_RESULT', ok: true, payload}
        sendResponse(result)
      } catch (error) {
        const result: ImportShareResult = {
          type: 'IMPORT_SHARE_RESULT',
          ok: false,
          error: error instanceof Error ? error.message : '导入失败',
        }
        sendResponse(result)
      }
    }
  }

  void respond()
  return true
})
