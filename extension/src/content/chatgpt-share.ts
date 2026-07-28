/**
 * Content script for ChatGPT share pages.
 * Keep this file free of shared imports so Vite will not emit a separate chunk
 * that Chrome content scripts cannot resolve.
 */

const BUTTON_ID = 'kk-khan-kiddo-import-btn'
const STYLE_ID = 'kk-khan-kiddo-import-style'

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
  `
  document.documentElement.appendChild(style)
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
