/** Resolve the active ChatGPT tab without requesting broader tabs permissions. */
export async function requireActiveChatGptTab() {
  const [tab] = await chrome.tabs.query({active: true, currentWindow: true})
  if (tab?.id == null || !tab.url || new URL(tab.url).origin !== 'https://chatgpt.com') {
    throw new Error('请在 ChatGPT 页面打开插件')
  }
  return {tabId: tab.id, url: new URL(tab.url)}
}
