/**
 * 轻量级消息提示工具（Toast）
 */
let toastEl: HTMLDivElement | null = null
let toastTimer: number | null = null

type MessageType = 'success' | 'error' | 'warning' | 'info'

function showToast(message: string, type: MessageType = 'info', duration = 2000) {
  if (toastEl) {
    if (toastTimer) {
      window.clearTimeout(toastTimer)
      toastTimer = null
    }
    toastEl.remove()
    toastEl = null
  }

  const el = document.createElement('div')
  el.className = 'sanda-toast'
  el.textContent = message

  const bgMap: Record<MessageType, string> = {
    success: '#52c41a',
    error: '#ff4d4f',
    warning: '#faad14',
    info: '#1a1a2e'
  }
  el.style.backgroundColor = bgMap[type]

  document.body.appendChild(el)
  toastEl = el

  toastTimer = window.setTimeout(() => {
    el.classList.add('sanda-toast-hide')
    window.setTimeout(() => {
      el.remove()
      toastEl = null
    }, 300)
  }, duration)
}

export const message = {
  success(msg: string, duration?: number) {
    showToast(msg, 'success', duration)
  },
  error(msg: string, duration?: number) {
    showToast(msg, 'error', duration)
  },
  warning(msg: string, duration?: number) {
    showToast(msg, 'warning', duration)
  },
  info(msg: string, duration?: number) {
    showToast(msg, 'info', duration)
  }
}

export default message
