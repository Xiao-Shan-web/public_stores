/**
 * 管理员端全局未读数状态（Pinia 单例）
 *
 * 统一管理"客服消息"未读总数：
 * - count：所有用户发来的未读消息汇总（/service/admin/unread-count）
 * - 左侧导航栏气泡、客服页头部角标均直接订阅此单一来源
 *
 * 更新方式（纯事件驱动，无定时轮询）：
 * 1. start() 时拉取一次服务端权威值
 * 2. WebSocket 收到用户消息：
 *    - 若该消息来自"当前正在聊天的用户"（activeUserId）→ 不计入未读，
 *      由聊天页自动标记已读，气泡不变化（避免先 +1 再 -1 的闪烁）
 *    - 若来自其他用户 → 乐观 +1，随后与服务端对账
 * 3. 任何页面执行已读操作后调用 refresh()，气泡实时减少
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { serviceAPI } from '@/api/serviceAPI'
import { subscribeService } from '@/composables/useGlobalSocket'
import { useAuthStore } from '@/stores/auth'

/** WS 推送后与服务端对账的延迟（ms），期间的多次推送合并为一次请求 */
const RECONCILE_DELAY = 400

export const useAdminUnreadStore = defineStore('adminUnread', () => {
  /** 客服未读消息总数 */
  const count = ref(0)
  /** 当前管理员正在聊天的用户ID（null 表示未打开任何会话） */
  const activeUserId = ref<number | null>(null)

  let unsubService: (() => void) | null = null
  let reconcileTimer: ReturnType<typeof setTimeout> | null = null
  let started = false

  /**
   * 设置当前正在聊天的用户ID
   * - 打开会话时传入该用户ID → 该用户后续消息不计入未读
   * - 离开会话时传入 null → 恢复正常未读计数
   */
  const setActiveUserId = (userId: number | null) => {
    activeUserId.value = userId
  }

  /** 从服务端拉取权威未读数 */
  const refresh = async () => {
    try {
      const res = await serviceAPI.adminUnreadCount()
      if (res.success && res.data) {
        count.value = res.data.count || 0
      }
    } catch {
      /* 静默，等待下一次事件刷新 */
    }
  }

  /**
   * 事件驱动的延迟对账：非当前会话的消息推送后先乐观 +1，短延迟后再拉取服务端真实值。
   * 注意：这不是定时器轮询，没有任何循环，仅在收到推送后触发一次。
   */
  const reconcileSoon = () => {
    if (reconcileTimer) clearTimeout(reconcileTimer)
    reconcileTimer = setTimeout(() => {
      reconcileTimer = null
      refresh()
    }, RECONCILE_DELAY)
  }

  /** 订阅 WebSocket 推送并拉取初始值（管理员端 Layout 挂载时调用，幂等） */
  const start = () => {
    if (started) return
    started = true
    const authStore = useAuthStore()

    unsubService = subscribeService((data) => {
      if (data.senderType === 'USER' && data.senderId !== authStore.userInfo?.id) {
        // 来自"当前正在聊天的用户"的消息：由聊天页自动已读，不计入未读，不产生气泡变化
        if (data.userId === activeUserId.value) return
        count.value += 1
        reconcileSoon()
      }
    })

    // 未读数属非关键信息：订阅先注册（不丢推送），初始值延后到空闲期再拉，
    // 不与首屏的登录态校验 / 控制台统计等接口争抢
    const w = window as any
    if (typeof w.requestIdleCallback === 'function') {
      w.requestIdleCallback(() => refresh(), { timeout: 1500 })
    } else {
      setTimeout(refresh, 300)
    }
  }

  /** 取消订阅并清零（退出登录 / 管理员端 Layout 卸载时调用） */
  const stop = () => {
    if (unsubService) {
      unsubService()
      unsubService = null
    }
    if (reconcileTimer) {
      clearTimeout(reconcileTimer)
      reconcileTimer = null
    }
    started = false
    activeUserId.value = null
    count.value = 0
  }

  return {
    count,
    activeUserId,
    setActiveUserId,
    refresh,
    start,
    stop
  }
})

export default useAdminUnreadStore
