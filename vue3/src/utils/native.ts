/**
 * 原生能力调用辅助
 * - getSimPhone: 读取 Android SIM 卡号（通过 Capacitor 插件）
 *   浏览器环境或插件未实现时返回空字符串，由调用方回退到登录页让用户手动输入
 */
import { Capacitor } from '@capacitor/core'
import { SimCard } from '@/plugins/simCard'

/**
 * 读取本机 SIM 卡号
 * @returns SIM 卡号字符串；非原生环境或读取失败返回 ''
 */
export const getSimPhone = async (): Promise<string> => {
  // 仅在原生 App 中尝试调用插件
  if (!Capacitor.isNativePlatform()) {
    return ''
  }

  try {
    const { value } = await SimCard.getPhoneNumber()
    // 去除 +86 前缀与空格
    if (!value) return ''
    const cleaned = value.replace(/[\s+]/g, '').replace(/^86/, '')
    return cleaned
  } catch (e) {
    // 插件未实现或权限被拒：静默回退
    return ''
  }
}

export default getSimPhone
