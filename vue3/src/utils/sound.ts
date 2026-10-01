/**
 * 消息提示音工具
 * 收到 WebSocket 新消息时调用 playMessageSound() 播放
 * 音频文件：src/static/audio/短信消息.mp3
 */

let audio: HTMLAudioElement | null = null
let audioSrc: string | null = null

/**
 * 预加载音频（避免首次播放延迟）
 * 使用 new URL(..., import.meta.url) 让 Vite 正确处理资源路径
 */
function ensureAudio(): HTMLAudioElement {
  if (!audio) {
    // Vite 会把这个 URL 在构建时解析为正确的资源路径
    audioSrc = new URL('../static/audio/短信消息.mp3', import.meta.url).href
    audio = new Audio(audioSrc)
    audio.preload = 'auto'
  }
  return audio
}

/**
 * 播放消息提示音
 * 浏览器策略要求用户交互后才能播放音频；
 * 如果被浏览器拦截（NotAllowedError），静默忽略
 */
export function playMessageSound() {
  try {
    const el = ensureAudio()
    // 重置到开头，允许连续播放
    el.currentTime = 0
    const p = el.play()
    if (p && typeof p.catch === 'function') {
      p.catch(() => {
        /* 浏览器策略阻止自动播放，静默 */
      })
    }
  } catch {
    /* 音频加载失败，静默 */
  }
}
