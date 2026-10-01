/**
 * Axios 请求封装
 * - baseURL /api（Vite 代理转发至后端）
 * - 请求拦截器：注入 JWT + 本地过期自动登出
 * - 响应拦截器：统一解包 { success, message, data, code }
 * - 业务码（与后端 Result 对齐）：
 *   200 成功 / 400 业务参数 / 401 未登录 / 403 无权限 / 404 不存在
 *   405 方法不支持 / 409 数据冲突 / 413 文件超限 / 429 限流幂等 / 500 系统繁忙
 * - 提示规则：不展示后端内部错误信息，统一友好提示；
 *   HTTP 非 2xx 与「HTTP 200 + success=false」两种失败都会在此处统一 toast，
 *   页面只需处理成功分支，避免漏写 else 导致失败无提示
 * - HTTP 错误统一顺序：超时 → 无响应 → 502/503/504 → 401（清登录态+跳登录）
 *   → 403 → 404 → 500 → 其他（优先后端 message）
 */
import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { message } from '@/utils/message'
import { useAuthStore } from '@/stores/auth'

export interface ApiResponse<T = any> {
  success: boolean
  message: string
  data: T
  code?: number
}

const service = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: true
})

// 登录/获取手机号等白名单（不需要 token 也允许发出请求）
const WHITELIST = ['/auth/login', '/auth/logout', '/admin/login']

/**
 * 跳转登录页：后台页面失效时必须带上 redirect，
 * 否则 Login 页会按默认（用户端手机号表单）展示，管理员看不到账号密码表单
 */
const redirectToLogin = () => {
  if (window.location.pathname.includes('/login')) return
  const target = window.location.pathname + window.location.search
  const loginUrl = target.startsWith('/admin')
    ? `/login?redirect=${encodeURIComponent(target)}`
    : '/login'
  window.location.href = loginUrl
}

// 请求拦截器：注入 token + 本地过期判断
service.interceptors.request.use(
  (config) => {
    const url = config.url || ''
    const authStore = useAuthStore()

    // 非白名单接口：本地过期判断
    if (!WHITELIST.some(p => url.includes(p))) {
      if (authStore.token && authStore.isTokenExpired) {
        // 本地已过期 → 主动登出，并引导重新登录
        authStore.logout()
        message.error('登录已过期，请重新登录')
        redirectToLogin()
        return Promise.reject(new Error('登录已过期'))
      }
    }

    if (authStore.token) {
      config.headers['Authorization'] = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/**
 * 按 HTTP status 取出后端 body 中的友好 message
 * GlobalExceptionHandler 返回体结构：{ success, message, code }
 */
const pickBackendMessage = (error: any): string | undefined => {
  const data = error?.response?.data
  if (data && typeof data === 'object' && typeof data.message === 'string') {
    return data.message
  }
  return undefined
}

// 响应拦截器：统一解包与错误处理
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const body = response.data

    if (body && body.success === false) {
      // 业务码 401 表示会话已失效（如旧 token 被服务端识别为失效）
      if (body.code === 401) {
        const authStore = useAuthStore()
        authStore.logout()
        message.error('登录已过期，请重新登录')
        redirectToLogin()
        return Promise.reject(new Error('登录已过期'))
      }

      // 其他业务失败：必须显式提示后端给出的具体原因。
      // 否则调用方若只判断 res.success（漏写 else 分支），失败会表现为「点了没反应」。
      // 这里不 reject：调用方仍可通过 res.success 走失败分支做本地状态回滚。
      message.error(body.message || '操作失败，请稍后重试')
    }

    return body as any
  },
  (error) => {
    // 统一错误处理顺序：超时 → 无响应 → 网关类 → 401 → 403 → 404 → 500 → 其他。
    // 只展示中文友好提示，不把英文 error.message 直接透出给用户；
    // 后端业务 message（如「手机号已注册」）始终优先展示，页面业务提示不受影响。
    const status = error?.response?.status
    const backendMsg = pickBackendMessage(error)
    let msg: string

    if (error?.code === 'ECONNABORTED') {
      // 1. 请求超时（axios 主动中断）
      msg = '请求超时，请稍后重试'
    } else if (!error?.response) {
      // 2. 无响应：后端未启动 / 网络中断 / DNS 解析失败等
      msg = '服务暂时不可用，请稍后重试'
    } else if (status === 502 || status === 503 || status === 504) {
      // 3. 网关 / 上游服务不可用
      msg = '服务暂时不可用，请稍后重试'
    } else if (status === 401) {
      // 4. 认证失败：清登录态 + 跳登录页。
      //    useAuthStore() 必须在回调内延迟调用：请求发出时 app.use(createPinia())
      //    已完成，禁止在模块顶层调用，否则 Pinia 未安装会直接抛错。
      //    后端 401 文案均为友好提示（账号被禁用 / 异地登录 / 过期），优先展示
      const authStore = useAuthStore()
      authStore.logout()
      msg = backendMsg || '登录已过期，请重新登录'
      redirectToLogin()
    } else if (status === 403) {
      // 5. 权限不足
      msg = '权限不足，无法操作'
    } else if (status === 404) {
      // 6. 资源不存在
      msg = '请求的资源不存在'
    } else if (status === 500) {
      // 7. 服务器内部错误：不暴露堆栈等内部信息
      msg = '服务器开小差了，请稍后重试'
    } else {
      // 8. 其他（400/405/409/413/429/501 等）：优先展示后端业务 message
      msg = backendMsg || '操作失败，请稍后重试'
    }

    message.error(msg)
    return Promise.reject(error)
  }
)

export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as unknown as Promise<ApiResponse<T>>
}

export default service
