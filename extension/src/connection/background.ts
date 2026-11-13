import {collectHistory} from './history'

export const CONNECTION_PROBE_STATUS = 'kk.connection.probeStatus'

// Keep reading if the popup closes.
export function registerConnectionProbe() {
  let reading = false
  chrome.runtime.onMessage.addListener((message, sender, respond) => {
    if (message?.type === 'CONNECTION_PROBE_HISTORY' && sender.url === chrome.runtime.getURL('popup.html')) {
      if (reading) { respond({error: '正在读取，请等待'}); return }
      reading = true
      respond({started: true})
      const save = async (value: object) => {
        await chrome.storage.session.set({[CONNECTION_PROBE_STATUS + message.tabId]: {conversationId: message.conversationId, ...value}})
      }
      void save({status: '正在滚动读取…'})
      void collectHistory(message.tabId, save).then(save).catch(error => save({error: String(error)})).finally(() => { reading = false })
      return
    }
  })
}
