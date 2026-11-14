// Passed to executeScript: keep this function self-contained.
export function injectComposerText(text: string): {ok: boolean; error?: string} {
  if (location.origin !== 'https://chatgpt.com' || location.pathname.startsWith('/share/')) {
    return {ok: false, error: '请打开 ChatGPT 新对话或已有对话页面'}
  }
  if (!text.trim()) return {ok: false, error: '示例内容为空'}
  if (document.querySelector('[data-testid="stop-button"]')) {
    return {ok: false, error: '请等待当前回复结束后再注入'}
  }
  const candidates = Array.from(document.querySelectorAll<HTMLElement>(
    '#prompt-textarea, [role="textbox"][contenteditable="true"], textarea[role="textbox"]',
  )).filter(element => {
    const style = getComputedStyle(element)
    return element.getClientRects().length > 0 && style.visibility !== 'hidden' &&
      style.visibility !== 'collapse' && !element.closest('[hidden], [aria-hidden="true"]')
  })
  if (candidates.length > 1) {
    return {ok: false, error: '找到多个可见输入框，请关闭其他编辑区域后重试'}
  }
  const editor = candidates[0]
  if (!editor) {
    return {ok: false, error: '未找到输入框，请等待页面加载完成后重试'}
  }
  const textarea = editor instanceof HTMLTextAreaElement
  if (!textarea && !editor.isContentEditable) return {ok: false, error: '当前输入框不支持注入'}
  if ((textarea ? editor.value : editor.innerText).trim()) {
    return {ok: false, error: '输入框已有草稿，请先发送或清空后再试'}
  }
  if (textarea ? editor.disabled || editor.readOnly : editor.getAttribute('aria-disabled') === 'true') {
    return {ok: false, error: '输入框暂不可用，请稍后重试'}
  }
  editor.focus()
  if (textarea) {
    const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set
    if (!setter) return {ok: false, error: '无法写入当前输入框'}
    setter.call(editor, text)
    editor.dispatchEvent(new Event('input', {bubbles: true}))
  } else {
    // Use a native editing operation so the editor receives its normal input
    // events and updates its document model. Never click the send button.
    const selection = window.getSelection()
    if (!selection) return {ok: false, error: '无法定位输入位置'}
    const range = document.createRange()
    range.selectNodeContents(editor)
    selection.removeAllRanges()
    selection.addRange(range)
    if (!document.execCommand('insertText', false, text)) {
      return {ok: false, error: '当前编辑器未接受注入，请手动粘贴示例'}
    }
  }
  const actual = textarea ? editor.value : editor.innerText
  return actual.trim() === text.trim() ? {ok: true} : {ok: false, error: '未能确认写入成功，请检查输入框'}
}
