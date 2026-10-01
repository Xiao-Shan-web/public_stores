/**
 * Capacitor SIM 卡读取插件（前端 Bridge）
 *
 * 原生端（Android）需注册名为 "SimCard" 的插件并提供 getPhoneNumber 方法。
 * 浏览器环境下 registerPlugin 不会抛错，但调用方法时会 reject（无原生实现），
 * 由 utils/native.ts 的 getSimPhone 统一捕获并回退。
 */
import { registerPlugin } from '@capacitor/core'

export interface SimCardPlugin {
  /** 读取本机 SIM 卡号，返回 { value }，失败返回空字符串 */
  getPhoneNumber(): Promise<{ value: string }>
}

// 注册插件：与 Android 端 SimCardPlugin.java 对应
export const SimCard = registerPlugin<SimCardPlugin>('SimCard')

export default SimCard
