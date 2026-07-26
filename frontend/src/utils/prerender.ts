/** Build-time prerender: signal when the current route is ready to snapshot. */
export function signalPrerenderReady(): void {
  if (typeof document === 'undefined') {
    return
  }
  document.dispatchEvent(new Event('prerender-ready'))
}
