/**
 * 客服通信接口（REST + WebSocket 约定）
 */
import { request } from './request'

/** 客服消息（与后端 ServiceChatService.buildVO 对齐） */
export interface ServiceMessage {
  id: number
  userId: number
  /** 发送者ID（USER 消息为用户ID，ADMIN 消息为客服ID；历史消息中可能为 null） */
  senderId?: number | null
  phone: string | null
  senderType: 'USER' | 'ADMIN'
  content: string
  /** 0-未读，1-已读（后端统一以 0/1 下发，判断用 Number(isRead) === 1） */
  isRead: number
  createdAt: string | null
}

/** 会话条目（客服端） */
export interface Conversation {
  userId: number
  phone: string | null
  /** 用户头像地址（后端下发后用于客服端展示，空则显示默认头像） */
  avatar?: string | null
  /** 用户头像缩略图（会话列表展示用） */
  avatarThumb?: string | null
  lastContent: string
  lastSenderType: 'USER' | 'ADMIN'
  lastTime: string | null
  unread: number
  online: boolean
}

export const serviceAPI = {
  /** 标记自己在线（进入页面） */
  online() {
    return request<void>({ method: 'post', url: '/service/online' })
  },

  /** 标记自己离线（离开页面） */
  offline() {
    return request<void>({ method: 'post', url: '/service/offline' })
  },

  /** 心跳续期（页面停留期间定时调用） */
  heartbeat() {
    return request<void>({ method: 'post', url: '/service/heartbeat' })
  },

  /** 在线状态：用户返回 { adminOnline }，客服返回 { online } */
  status() {
    return request<{ adminOnline?: boolean; online?: boolean }>({
      method: 'get',
      url: '/service/status'
    })
  },

  /** 用户未读客服消息数 */
  unread() {
    return request<{ count: number }>({ method: 'get', url: '/service/unread' })
  },

  /** 管理员未读消息总数（所有用户发来的未读汇总） */
  adminUnreadCount() {
    return request<{ count: number }>({ method: 'get', url: '/service/admin/unread-count' })
  },

  /** 会话列表（客服端） */
  conversations() {
    return request<Conversation[]>({ method: 'get', url: '/service/conversations' })
  },

  /** 历史消息（用户不传 userId；客服传目标用户ID） */
  messages(params: { userId?: number; page?: number; size?: number } = {}) {
    return request<{ list: ServiceMessage[]; total: number; page: number }>({
      method: 'get',
      url: '/service/messages',
      params: {
        userId: params.userId,
        page: params.page || 1,
        size: params.size || 20
      }
    })
  },

  /** 用户标记客服消息全部已读 */
  markRead() {
    return request<void>({ method: 'post', url: '/service/read' })
  },

  /** 客服标记某用户消息已读 */
  markUserRead(userId: number) {
    return request<void>({ method: 'post', url: `/service/${userId}/read` })
  },

  /** 客服标记某用户的全部未读消息已读（管理员端专用接口） */
  adminReadAll(userId: number) {
    return request<void>({
      method: 'post',
      url: `/admin/service/messages/read-all/${userId}`
    })
  },

  /** REST 发送消息（WebSocket 兜底） */
  send(payload: { content: string; toUserId?: number }) {
    return request<ServiceMessage>({ method: 'post', url: '/service/send', data: payload })
  }
}

/** WebSocket 目的地约定（与后端 WebSocketConfig / ServiceSocketController 对齐） */
export const SERVICE_WS = {
  /** 端点 */
  ENDPOINT: '/ws',
  /** 客户端发送目的地 */
  SEND: '/app/service.send',
  /** 接收推送的订阅地址（STOMP 自动拼接 /user 前缀） */
  SUBSCRIBE: '/queue/service'
}

export default serviceAPI
