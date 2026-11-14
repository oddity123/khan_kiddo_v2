import {requireActiveChatGptTab} from '../shared/chatgptTab.ts'
import {injectComposerText} from './inject'

const EXAMPLE_SENTENCE = 'Please help me practice English. Ask me one simple question about my day.'

export function mountInjectionExample() {
  const section = document.getElementById('injectionSection')!
  section.classList.remove('hidden')
  section.querySelector('#injectionExample')!.textContent = EXAMPLE_SENTENCE
  const button = section.querySelector<HTMLButtonElement>('#injectExample')!
  const status = section.querySelector<HTMLElement>('#injectionStatus')!
  button.addEventListener('click', async () => {
    button.disabled = true
    status.className = 'status'
    status.textContent = '正在填入输入框…'
    try {
      const {tabId} = await requireActiveChatGptTab()
      const [frame] = await chrome.scripting.executeScript({
        target: {tabId}, func: injectComposerText, args: [EXAMPLE_SENTENCE],
      })
      if (!frame?.result?.ok) throw new Error(frame?.result?.error ?? '未能连接页面，请刷新 ChatGPT 后重试')
      status.textContent = '已填入 ChatGPT 输入框，请检查后手动发送。'
      status.className = 'status ok'
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : '注入失败，请重试'
      status.className = 'status error'
    } finally {
      button.disabled = false
    }
  })
}
