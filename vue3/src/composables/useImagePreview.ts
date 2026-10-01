/**
 * 全局图片放大预览（模块级单例）
 *
 * 用法：
 * const { open } = useImagePreview()
 * open(url)   // 任意位置调用即可弹出全屏遮罩
 *
 * 遮罩 UI 由 <ImagePreview /> 统一渲染（在 App.vue 挂载一次），
 * 调用方无需各自维护 preview-mask 节点。
 */
import { ref } from 'vue'

/** 当前预览地址；空串表示关闭 */
const previewUrl = ref('')

export function useImagePreview() {
  /** 打开预览（空地址忽略） */
  const open = (url?: string | null) => {
    if (url && url.trim()) previewUrl.value = url
  }
  /** 关闭预览 */
  const close = () => {
    previewUrl.value = ''
  }
  return { previewUrl, open, close }
}

export default useImagePreview
