/**
 * 用户端全局未读数状态（Pinia 单例）
 *
 * 统一管理底部导航"消息"气泡：
 * - noticeCount：系统通知未读（/message/unread-count）
 * - serviceCount：客服消息未读（/service/unread）
 * - total：两者之和，导航栏气泡直接订阅
 *
 * 更新方式（纯事件驱动，无定时轮询）：
 * 1. start() 时拉取一次服务端权威值
 * 2. WebSocket 收到推送 → 先乐观 +1（即时反馈），再在短延迟内与服务端对账一次，
 *    对账用于纠正"聊天页/通知页已自动已读"的场景（合并 400ms 内的连续推送，只发一次请求）
 * 3. 任何页面执行已读操作后调用 refresh()，气泡实时减少
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { messageAPI } from '@/api/messageAPI'
import { serviceAPI } from '@/api/serviceAPI'
import { subscribeService, subscribeNotification } from '@/composables/useGlobalSocket'
import { useAuthStore } from '@/stores/auth'

/** WS 推送后与服务端对账的延迟（ms），期间的多次推送合并为一次请求 */
const RECONCILE_DELAY = 400

export const useUnreadStore = defineStore('userUnread', () => {
  /** 系统通知未读数 */
  const noticeCount = ref(0)
  /** 客服消息未读数 */
  const serviceCount = ref(0)
  /** 总未读数（导航栏气泡） */
  const total = computed(() => noticeCount.value + serviceCount.value)

  let unsubService: (() => void) | null = null
  let unsubNotification: (() => void) | null = null
  let reconcileTimer: ReturnType<typeof setTimeout> | null = null
  let started = false

  /** 从服务端拉取权威未读数（通知 + 客服） */
  const refresh = async () => {
    try {
      const [noticeRes, serviceRes] = await Promise.all([
        messageAPI.unreadCount(),
        serviceAPI.unread()
      ])
      if (noticeRes.success && noticeRes.data) {
        noticeCount.value = noticeRes.data.count || 0
      }
      if (serviceRes.success && serviceRes.data) {
        serviceCount.value = serviceRes.data.count || 0
      }
    } catch {
      /* 静默，等待下一次事件刷新 */
    }
  }

  /**
   * 事件驱动的延迟对账：WS 推送后先乐观 +1，短延迟后再拉取服务端真实值。
   * 自动已读场景（停留在聊天页/刚进入通知页）由此被纠正为 0。
   * 注意：这不是定时器轮询，没有任何循环，仅在收到推送后触发一次。
   */
  const reconcileSoon = () => {
    if (reconcileTimer) clearTimeout(reconcileTimer)
    reconcileTimer = setTimeout(() => {
      reconcileTimer = null
      refresh()
    }, RECONCILE_DELAY)
  }

  /** 订阅 WebSocket 推送并拉取初始值（在用户端 Layout 挂载时调用，幂等） */
  const start = () => {
    if (started) return
    started = true
    const authStore = useAuthStore()

    // 客服消息：仅客服发来的（非本人）计入未读
    unsubService = subscribeService((data) => {
      if (data.senderType === 'ADMIN' && data.senderId !== authStore.userInfo?.id) {
        serviceCount.value += 1
      }
      reconcileSoon()
    })

    // 系统通知：推送即未读
    unsubNotification = subscribeNotification(() => {
      noticeCount.value += 1
      reconcileSoon()
    })

    refresh()
  }

  /** 取消订阅并清零（退出登录 / 用户端 Layout 卸载时调用） */
  const stop = () => {
    if (unsubService) {
      unsubService()
      unsubService = null
    }
    if (unsubNotification) {
      unsubNotification()
      unsubNotification = null
    }
    if (reconcileTimer) {
      clearTimeout(reconcileTimer)
      reconcileTimer = null
    }
    started = false
    noticeCount.value = 0
    serviceCount.value = 0
  }

  return {
    noticeCount,
    serviceCount,
    total,
    refresh,
    start,
    stop
  }
})

export default useUnreadStore
