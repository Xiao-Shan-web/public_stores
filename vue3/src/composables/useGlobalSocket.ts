/**
 * 全局 WebSocket 客户端（单例）
 *
 * 登录后由 auth store 调用 connect() 建立长连接，断线自动重连；登出/关页调用 disconnect()。
 *
 * 订阅（按角色）：
 * - 用户端：/user/queue/service（客服消息）、/user/queue/notification（系统通知）、
 *           /user/queue/service-status（客服上下线实时变化）
 * - 管理员端：/user/queue/service（用户客服消息）、
 *             /user/queue/service-user-status（用户上下线实时变化）
 *
 * 在线状态（Redis）由后端按 WS 连接生命周期维护：
 * - 连接建立（首条）→ 写 Redis + 广播上线；断开（末条）→ 删 Redis + 广播下线
 * - 前端无需再调用 online/offline/heartbeat REST 接口
 *
 * 设计：
 * - 模块级单例 client/handlers/订阅者管理，不依赖组件实例
 * - 页面通过 subscribeXxx 注册回调，只更新对应状态标识，不刷新整页
 */
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

export interface ServiceMessageData {
  id: number
  userId: number
  /** 发送者ID（USER 消息为用户ID，ADMIN 消息为客服ID；历史消息中可能为 null） */
  senderId: number | null
  phone: string | null
  senderType: 'USER' | 'ADMIN'
  content: string
  /** 0-未读，1-已读（后端统一以 0/1 下发，判断用 Number(isRead) === 1） */
  isRead: number
  createdAt: string | null
}

export interface NotificationData {
  id: number
  userId: number
  type: string
  title: string
  content: string
  refId: number | null
  /** 0-未读，1-已读（后端统一以 0/1 下发，判断用 Number(isRead) === 1） */
  isRead: number
  createdAt: string | null
}

/** 用户端收到的客服在线状态变化（type 由后端下发） */
export interface ServiceStatusData {
  type: 'ADMIN_ONLINE' | 'ADMIN_OFFLINE'
}

/** 管理员端收到的用户在线状态变化 */
export interface UserStatusData {
  type: 'USER_ONLINE' | 'USER_OFFLINE'
  userId: number
}

type ServiceHandler = (msg: ServiceMessageData) => void
type NotificationHandler = (msg: NotificationData) => void
type StateHandler = (connected: boolean) => void
/** 用户端：客服在线状态回调（true=在线 false=离线） */
type ServiceStatusHandler = (adminOnline: boolean) => void
/** 管理员端：用户在线状态回调 */
type UserStatusHandler = (payload: { userId: number; online: boolean }) => void

let client: Client | null = null

/** 客服消息订阅者（聊天页、消息中心等） */
const serviceSubscribers = new Set<ServiceHandler>()
/** 系统通知订阅者（通知列表页、消息中心、Layout Badge 等） */
const notificationSubscribers = new Set<NotificationHandler>()
/** 连接状态订阅者（Layout 显示 WS 状态等） */
const stateSubscribers = new Set<StateHandler>()
/** 用户端：客服上下线订阅者 */
const serviceStatusSubscribers = new Set<ServiceStatusHandler>()
/** 管理员端：用户上下线订阅者 */
const userStatusSubscribers = new Set<UserStatusHandler>()

let wsConnected = false

/** 当前连接的身份（用于过滤自己发出的消息回传） */
let selfUserType: 'user' | 'admin' | null = null
let selfId: number | null = null

/**
 * 最近已分发消息的 id 缓存（防止重连重复订阅 / 服务端重复投递导致同一条消息处理多次）。
 * key 形如 `service:ADMIN:123` / `notification:456`，超过容量淘汰最早条目。
 */
const RECENT_ID_CAP = 200
const recentDeliveredIds = new Map<string, true>()

const markSeen = (key: string): boolean => {
  if (recentDeliveredIds.has(key)) return false
  recentDeliveredIds.set(key, true)
  if (recentDeliveredIds.size > RECENT_ID_CAP) {
    const oldest = recentDeliveredIds.keys().next().value
    if (oldest !== undefined) recentDeliveredIds.delete(oldest)
  }
  return true
}

/** 是否为"自己发出的消息"被回传（后端不应回推，此处为双保险） */
const isSelfServiceMessage = (data: ServiceMessageData): boolean => {
  if (selfId == null) return false
  return selfUserType === 'user'
    ? data.senderType === 'USER' && data.senderId === selfId
    : data.senderType === 'ADMIN' && data.senderId === selfId
}

/**
 * 当前连接状态
 */
export function isWsConnected() {
  return wsConnected
}

/**
 * 订阅客服消息
 * @returns 取消订阅函数
 */
export function subscribeService(handler: ServiceHandler) {
  serviceSubscribers.add(handler)
  return () => serviceSubscribers.delete(handler)
}

/**
 * 订阅系统通知
 * @returns 取消订阅函数
 */
export function subscribeNotification(handler: NotificationHandler) {
  notificationSubscribers.add(handler)
  return () => notificationSubscribers.delete(handler)
}

/**
 * 订阅连接状态变化
 * @returns 取消订阅函数
 */
export function subscribeState(handler: StateHandler) {
  stateSubscribers.add(handler)
  handler(wsConnected) // 立即同步当前状态
  return () => stateSubscribers.delete(handler)
}

/**
 * 用户端：订阅客服在线状态变化（管理员上/下线）
 * 收到后只需更新「客服在线/离线」标识，无需重新请求接口
 * @returns 取消订阅函数
 */
export function subscribeServiceStatus(handler: ServiceStatusHandler) {
  serviceStatusSubscribers.add(handler)
  return () => serviceStatusSubscribers.delete(handler)
}

/**
 * 管理员端：订阅用户在线状态变化（任意用户上/下线）
 * 收到后按 userId 更新会话列表对应用户的在线标识
 * @returns 取消订阅函数
 */
export function subscribeUserStatus(handler: UserStatusHandler) {
  userStatusSubscribers.add(handler)
  return () => userStatusSubscribers.delete(handler)
}

/**
 * 发送客服消息（通过已建立的全局连接）
 * @returns true 表示已发送，false 表示未连接
 */
export function sendServiceMessage(payload: { content: string; toUserId?: number }) {
  if (!client || !client.connected) return false
  client.publish({
    destination: '/app/service.send',
    body: JSON.stringify(payload)
  })
  return true
}

/**
 * 建立全局 WebSocket 连接（幂等，已连接不会重复建立）
 * @param opts.userType 'user' 普通用户 / 'admin' 管理员
 *   - user：订阅客服消息 + 系统通知 + 客服上下线状态
 *   - admin：订阅用户客服消息 + 用户上下线状态
 * @param opts.selfId 当前登录者ID（用户ID 或 管理员ID），用于过滤自己消息的回传
 */
export function connectGlobalSocket(opts?: { userType?: string; selfId?: number }) {
  if (client && (client.active || client.connected)) return
  const userType = opts?.userType
  selfUserType = userType === 'admin' ? 'admin' : 'user'
  selfId = opts?.selfId ?? null

  /**
   * 当前 client 已创建的 STOMP 订阅。
   * stompjs 在自动重连后会尝试恢复旧订阅，因此每次 onConnect 必须先退订旧的、
   * 再重新订阅，否则同一目的地会存在多个订阅，同一条消息被投递多次（消息重复/提示音重复）。
   */
  let stompSubs: StompSubscription[] = []

  const clearSubscriptions = () => {
    stompSubs.forEach(s => {
      try {
        s.unsubscribe()
      } catch {
        /* 订阅可能已随连接失效，忽略 */
      }
    })
    stompSubs = []
  }

  client = new Client({
    webSocketFactory: () => new SockJS('/ws') as unknown as WebSocket,
    reconnectDelay: 5000,
    maxReconnectDelay: 30000,

    onConnect: () => {
      wsConnected = true
      stateSubscribers.forEach(h => h(true))

      // 重连场景：先清掉上一轮订阅，保证每个目的地只有一个有效订阅
      clearSubscriptions()

      // 订阅客服消息（用户端 + 管理员端均订阅）
      stompSubs.push(client!.subscribe('/user/queue/service', (frame: IMessage) => {
        try {
          const data = JSON.parse(frame.body) as ServiceMessageData
          // 自己发出的消息被回传 → 忽略（后端本就不回推，双保险）
          if (isSelfServiceMessage(data)) return
          // 帧级去重：同一消息 id 只分发一次（防止重复投递）
          if (!markSeen(`service:${data.senderType}:${data.id}`)) return
          serviceSubscribers.forEach(h => h(data))
        } catch {
          /* 非 JSON 载荷忽略 */
        }
      }))

      // 错误回执
      stompSubs.push(client!.subscribe('/user/queue/service.error', (frame: IMessage) => {
        try {
          const data = JSON.parse(frame.body)
          if (data?.error) console.warn('[WS] service error:', data.error)
        } catch {
          /* 忽略 */
        }
      }))

      if (userType === 'admin') {
        // 管理员端：订阅用户上下线状态 → 实时刷新会话列表对应用户在线标识
        stompSubs.push(client!.subscribe('/user/queue/service-user-status', (frame: IMessage) => {
          try {
            const data = JSON.parse(frame.body) as UserStatusData
            const online = data.type === 'USER_ONLINE'
            userStatusSubscribers.forEach(h => h({ userId: data.userId, online }))
          } catch {
            /* 非 JSON 载荷忽略 */
          }
        }))
      } else {
        // 用户端：订阅系统通知（帧级去重）
        stompSubs.push(client!.subscribe('/user/queue/notification', (frame: IMessage) => {
          try {
            const data = JSON.parse(frame.body) as NotificationData
            if (!markSeen(`notification:${data.id}`)) return
            notificationSubscribers.forEach(h => h(data))
          } catch {
            /* 非 JSON 载荷忽略 */
          }
        }))
        // 用户端：订阅客服上下线状态 → 实时更新「客服在线/离线」
        stompSubs.push(client!.subscribe('/user/queue/service-status', (frame: IMessage) => {
          try {
            const data = JSON.parse(frame.body) as ServiceStatusData
            const online = data.type === 'ADMIN_ONLINE'
            serviceStatusSubscribers.forEach(h => h(online))
          } catch {
            /* 非 JSON 载荷忽略 */
          }
        }))
      }

      // 在线状态（Redis）由后端按连接生命周期维护，前端无需上报
    },

    onWebSocketClose: () => {
      wsConnected = false
      stateSubscribers.forEach(h => h(false))
    },

    onStompError: frame => {
      console.error('[WS] STOMP 协议错误', frame.headers['message'])
      wsConnected = false
      stateSubscribers.forEach(h => h(false))
    }
  })

  client.activate()
}

/**
 * 断开全局 WebSocket 连接
 * - 后端监听到连接断开后会删除 Redis 在线标记并广播下线
 * - 退出登录 / 页面关闭时调用
 */
export function disconnectGlobalSocket() {
  if (client) {
    client.deactivate()
    client = null
  }
  wsConnected = false
  selfUserType = null
  selfId = null
  recentDeliveredIds.clear()
  stateSubscribers.forEach(h => h(false))
}
