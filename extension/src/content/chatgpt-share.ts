/**
 * Content script for ChatGPT share pages.
 * Keep this file free of shared imports so Vite will not emit a separate chunk
 * that Chrome content scripts cannot resolve.
 */

const BUTTON_ID = 'kk-khan-kiddo-import-btn'
const STYLE_ID = 'kk-khan-kiddo-import-style'
const CONSENT_DIALOG_ID = 'kk-khan-kiddo-consent-dialog'
// Keep in sync with shared/constants.ts. This content script intentionally has no imports.
const PRIVACY_CONSENT_STORAGE_KEY = 'kk.extension.privacyConsent.v1'
const PRIVACY_POLICY_URL = 'https://khankiddo.top/privacy/extension'

function ensureStyles(): void {
  if (document.getElementById(STYLE_ID)) {
    return
  }
  const style = document.createElement('style')
  style.id = STYLE_ID
  style.textContent = `
    #${BUTTON_ID} {
      position: fixed;
      right: 20px;
      bottom: 24px;
      z-index: 2147483646;
      border: none;
      border-radius: 999px;
      padding: 12px 18px;
      font: 600 14px/1.2 system-ui, -apple-system, sans-serif;
      color: #f8fafc;
      background: #0b1a7d;
      box-shadow: 0 8px 24px rgba(11, 26, 125, 0.28);
      cursor: pointer;
    }
    #${BUTTON_ID}:hover { filter: brightness(1.08); }
    #${BUTTON_ID}:disabled {
      opacity: 0.7;
      cursor: wait;
    }
    #${BUTTON_ID}[data-state="error"] {
      background: #9f1239;
    }
    #${BUTTON_ID}[data-state="ok"] {
      background: #166534;
    }
    #${CONSENT_DIALOG_ID} {
      position: fixed;
      inset: 0;
      z-index: 2147483647;
      display: grid;
      place-items: center;
      padding: 20px;
      background: rgba(5, 10, 32, 0.58);
      backdrop-filter: blur(8px);
      font-family: "DM Sans", system-ui, -apple-system, sans-serif;
    }
    #${CONSENT_DIALOG_ID} .kk-consent-card {
      width: min(430px, calc(100vw - 40px));
      overflow: hidden;
      border: 1px solid rgba(255, 255, 255, 0.75);
      border-radius: 20px;
      color: #141824;
      background: rgba(255, 255, 255, 0.97);
      box-shadow: 0 28px 70px rgba(5, 10, 32, 0.34);
    }
    #${CONSENT_DIALOG_ID} .kk-consent-accent {
      height: 5px;
      background: linear-gradient(90deg, #0b1a7d 0 72%, #b8941f 72% 100%);
    }
    #${CONSENT_DIALOG_ID} .kk-consent-body {
      padding: 24px 24px 20px;
    }
    #${CONSENT_DIALOG_ID} .kk-consent-kicker {
      margin: 0 0 8px;
      color: #8a6b0c;
      font: 700 11px/1.2 "IBM Plex Mono", ui-monospace, monospace;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    #${CONSENT_DIALOG_ID} h2 {
      margin: 0 0 10px;
      color: #0b1a7d;
      font: 700 22px/1.2 Georgia, "Songti SC", serif;
    }
    #${CONSENT_DIALOG_ID} p {
      margin: 0;
      color: #4b5568;
      font-size: 13px;
      line-height: 1.65;
    }
    #${CONSENT_DIALOG_ID} ul {
      margin: 14px 0;
      padding: 12px 12px 12px 30px;
      border-radius: 12px;
      color: #30384e;
      background: #f3f5fa;
      font-size: 12px;
      line-height: 1.65;
    }
    #${CONSENT_DIALOG_ID} a {
      color: #0b1a7d;
      font-weight: 700;
    }
    #${CONSENT_DIALOG_ID} .kk-consent-actions {
      display: flex;
      gap: 10px;
      margin-top: 20px;
    }
    #${CONSENT_DIALOG_ID} button {
      flex: 1;
      border: 0;
      border-radius: 10px;
      padding: 10px 14px;
      cursor: pointer;
      font: 700 13px/1.2 "DM Sans", system-ui, sans-serif;
    }
    #${CONSENT_DIALOG_ID} .kk-consent-cancel {
      color: #475569;
      background: #e8ebf2;
    }
    #${CONSENT_DIALOG_ID} .kk-consent-accept {
      color: #fff;
      background: #0b1a7d;
      box-shadow: 0 10px 24px rgba(11, 26, 125, 0.24);
    }
  `
  document.documentElement.appendChild(style)
}

async function requirePrivacyConsent(): Promise<boolean> {
  const stored = await chrome.storage.local.get(PRIVACY_CONSENT_STORAGE_KEY)
  if (stored[PRIVACY_CONSENT_STORAGE_KEY] === true) {
    return true
  }

  return new Promise((resolve) => {
    document.getElementById(CONSENT_DIALOG_ID)?.remove()
    const dialog = document.createElement('div')
    dialog.id = CONSENT_DIALOG_ID
    dialog.setAttribute('role', 'dialog')
    dialog.setAttribute('aria-modal', 'true')
    dialog.setAttribute('aria-labelledby', `${CONSENT_DIALOG_ID}-title`)
    dialog.innerHTML = `
      <div class="kk-consent-card">
        <div class="kk-consent-accent"></div>
        <div class="kk-consent-body">
          <p class="kk-consent-kicker">Before importing</p>
          <h2 id="${CONSENT_DIALOG_ID}-title">导入前的数据说明</h2>
          <p>只有在你主动确认后，Khan Kiddo 才会处理当前公开分享页的数据：</p>
          <ul>
            <li>读取分享链接、标题和对话正文；</li>
            <li>在本机整理内容，并临时传递到 Khan Kiddo 分析页；</li>
            <li>不会读取 ChatGPT 密码、登录 Cookie 或私密会话。</li>
          </ul>
          <p>继续即表示你同意上述用途。你可以在扩展弹窗中取消同意。详情见
            <a href="${PRIVACY_POLICY_URL}" target="_blank" rel="noreferrer">扩展隐私政策</a>。
          </p>
          <div class="kk-consent-actions">
            <button type="button" class="kk-consent-cancel">暂不导入</button>
            <button type="button" class="kk-consent-accept">同意并继续</button>
          </div>
        </div>
      </div>
    `

    const finish = (accepted: boolean) => {
      document.removeEventListener('keydown', onKeydown)
      dialog.remove()
      resolve(accepted)
    }
    const onKeydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        finish(false)
      }
    }
    dialog.querySelector<HTMLButtonElement>('.kk-consent-cancel')?.addEventListener('click', () => finish(false))
    dialog.querySelector<HTMLButtonElement>('.kk-consent-accept')?.addEventListener('click', () => {
      void chrome.storage.local.set({[PRIVACY_CONSENT_STORAGE_KEY]: true}).then(() => finish(true))
    })
    document.addEventListener('keydown', onKeydown)
    document.documentElement.appendChild(dialog)
    dialog.querySelector<HTMLButtonElement>('.kk-consent-accept')?.focus()
  })
}

function setButtonState(btn: HTMLButtonElement, label: string, state: 'idle' | 'loading' | 'ok' | 'error'): void {
  btn.textContent = label
  btn.dataset.state = state
  btn.disabled = state === 'loading'
}

function ensureButton(): HTMLButtonElement {
  ensureStyles()
  let btn = document.getElementById(BUTTON_ID) as HTMLButtonElement | null
  if (btn) {
    return btn
  }
  btn = document.createElement('button')
  btn.id = BUTTON_ID
  btn.type = 'button'
  setButtonState(btn, '导入到 Khan Kiddo', 'idle')
  btn.addEventListener('click', () => {
    void onImportClick(btn!)
  })
  document.documentElement.appendChild(btn)
  return btn
}

async function onImportClick(btn: HTMLButtonElement): Promise<void> {
  const consented = await requirePrivacyConsent()
  if (!consented) {
    setButtonState(btn, '导入到 Khan Kiddo', 'idle')
    return
  }
  setButtonState(btn, '正在导入…', 'loading')
  try {
    const result = await chrome.runtime.sendMessage({
      type: 'IMPORT_SHARE',
      shareUrl: window.location.href,
    })
    if (!result || result.ok !== true) {
      const err = (result && result.error) || '导入失败'
      setButtonState(btn, '导入失败，点击重试', 'error')
      console.warn('[Khan Kiddo]', err)
      window.setTimeout(() => setButtonState(btn, '导入到 Khan Kiddo', 'idle'), 3200)
      return
    }
    const count = result.payload?.messageCount ?? 0
    setButtonState(btn, `已导入 ${count} 条`, 'ok')
    window.setTimeout(() => setButtonState(btn, '导入到 Khan Kiddo', 'idle'), 2400)
  } catch (error) {
    console.warn('[Khan Kiddo]', error)
    setButtonState(btn, '导入失败，点击重试', 'error')
    window.setTimeout(() => setButtonState(btn, '导入到 Khan Kiddo', 'idle'), 3200)
  }
}

function boot(): void {
  if (!/\/share\//.test(window.location.pathname)) {
    return
  }
  ensureButton()
}

boot()
