/**
 * 认证状态管理（Pinia）
 * 青春热血风：橙色主题配套
 * 存储：token + expiresAt（用于过期判断） + userType（分流恢复） + userInfo
 */
import { defineStore } from 'pinia'
import { authAPI } from '@/api/authAPI'
import { connectGlobalSocket, disconnectGlobalSocket } from '@/composables/useGlobalSocket'

/** Token 在 localStorage 的键 */
const TOKEN_KEY = 'token'
/** 过期时间在 localStorage 的键 */
const EXPIRES_AT_KEY = 'tokenExpiresAt'
/** 用户类型在 localStorage 的键（user / admin，用于刷新时分流恢复） */
const TYPE_KEY = 'tokenType'

/** 进行中的登录态恢复 Promise（单飞，防止路由守卫与 App 挂载各发一次 user-info） */
let restorePromise: Promise<boolean> | null = null
/** WebSocket 延后连接的代号：登出 / 重新登录时使尚未执行的空闲回调失效 */
let socketScheduleSeq = 0

/**
 * 在浏览器空闲期执行回调（不支持 requestIdleCallback 时退化为短延时），
 * 用于把 WebSocket 等非关键操作推迟到首屏渲染之后。
 */
const runWhenIdle = (cb: () => void, timeout = 1500) => {
  const ric = window.requestIdleCallback
  if (typeof ric === 'function') {
    ric(cb, { timeout })
  } else {
    setTimeout(cb, 300)
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    expiresAt: Number(localStorage.getItem(EXPIRES_AT_KEY)) || 0,
    userType: localStorage.getItem(TYPE_KEY) || '',
    userId: null as number | null,
    userInfo: null as any,
    isLoggedIn: false
  }),

  getters: {
    getToken: (state) => state.token,
    getUserId: (state) => state.userId,
    getIsLoggedIn: (state) => state.isLoggedIn,
    /** Token 是否已过期（无 expiresAt 视为过期） */
    isTokenExpired: (state) => {
      if (!state.token || !state.expiresAt) return true
      return Date.now() >= state.expiresAt
    }
  },

  actions: {
    /**
     * 保存 Token + 过期时间 + 类型到 state 与 localStorage
     * type: 'user' 普通用户 / 'admin' 管理员，刷新时按此分流调用不同接口
     */
    setToken(token: string, expiresAt?: number, type?: string) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
      if (expiresAt) {
        this.expiresAt = expiresAt
        localStorage.setItem(EXPIRES_AT_KEY, String(expiresAt))
      }
      if (type) {
        this.userType = type
        localStorage.setItem(TYPE_KEY, type)
      }
    },

    setUserInfo(info: any) {
      this.userInfo = info
      this.userId = info?.id || null
      this.isLoggedIn = true
      // WebSocket 延后到首屏渲染后的空闲期再连接：
      // 不与首屏接口/渲染抢资源；连接是幂等的，重复设置用户信息不会建多条连接。
      // 用户端订阅客服消息 + 系统通知；管理员端仅订阅客服消息，
      // 传入 selfId 用于过滤自己消息的回传
      const seq = ++socketScheduleSeq
      runWhenIdle(() => {
        // 等待期间已登出或又设置了新的登录信息 → 本次调度作废
        if (seq !== socketScheduleSeq) return
        connectGlobalSocket({ userType: this.userType, selfId: info?.id })
      })
    },

    /**
     * 局部合并用户信息（如编辑资料后同步头像/昵称）
     * 仅更新内存态，不重连 WebSocket、不改变登录状态
     */
    patchUserInfo(patch: Record<string, any>) {
      if (!this.userInfo) {
        this.userInfo = {}
      }
      this.userInfo = { ...this.userInfo, ...patch }
    },

    logout() {
      // 作废尚未执行的延后 WebSocket 连接，并断开已有连接
      socketScheduleSeq++
      restorePromise = null
      disconnectGlobalSocket()
      this.token = ''
      this.expiresAt = 0
      this.userType = ''
      this.userId = null
      this.userInfo = null
      this.isLoggedIn = false
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(EXPIRES_AT_KEY)
      localStorage.removeItem(TYPE_KEY)
    },

    /**
     * 检查登录状态（刷新页面恢复登录态）——单飞：
     * 多次调用复用同一个请求，避免路由守卫与 App 挂载各发一次 user-info。
     * 1. 本地 expiresAt 已过期 → 直接登出
     * 2. 按 userType 分流：admin 调 /admin/user-info，user 调 /auth/user-info
     * 注意：该方法不阻塞首屏渲染，路由守卫只依据本地 token 同步放行，
     * 服务端校验在后台异步进行，失败时再登出并跳登录页。
     */
    checkLoginStatus() {
      if (this.isLoggedIn) return Promise.resolve(true)

      // 本地过期判断（避免无效请求）
      if (this.isTokenExpired || !this.token) {
        this.logout()
        return Promise.resolve(false)
      }

      // 已有进行中的恢复请求 → 直接复用
      if (restorePromise) return restorePromise

      restorePromise = (async () => {
        try {
          const res =
            this.userType === 'admin'
              ? await authAPI.getAdminInfo()
              : await authAPI.getUserInfo()
          if (res.success && res.data?.id) {
            this.setUserInfo(res.data)
            return true
          }
        } catch {
          /* 401 等错误已由响应拦截器统一处理（登出 + 跳登录页） */
        }
        this.logout()
        return false
      })()

      // 完成后释放单飞占位（后续调用按最新登录态决定）
      restorePromise.finally(() => {
        restorePromise = null
      })
      return restorePromise
    }
  }
})

export default useAuthStore
