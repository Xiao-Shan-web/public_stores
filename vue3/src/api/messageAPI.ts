/**
 * 消息通知接口
 */
import { request } from './request'

export interface MessageItem {
  id: number
  /**
   * 与后端 Message.type 一一对应。
   * 新增取值必须同步 Notifications.vue 的 typeIcon / typeClass / .tp-* 配色，
   * 否则会落到默认的通用铃铛图标。
   */
  type: 'VERIFY' | 'EXPIRE' | 'SYSTEM' | 'ACTIVITY' | 'INTERACT' | 'AI_PLAN' | 'COMPLAINT'
  title: string
  content: string
  refId?: number | null
  /** 0-未读，1-已读（后端 TINYINT(1) 统一以 0/1 下发，判断用 Number(isRead) === 1） */
  isRead: number
  createdAt: string
}

export interface MessageListResult {
  list: MessageItem[]
  total: number
}

/** 管理端：已发通知批次（同一次群发聚合成一行） */
export interface AdminNotificationBatch {
  /** 批次ID（用于删除） */
  batchId: number
  title: string
  type: string
  /** 发送范围：ALL 全部用户 / SEGMENT 分人群 / SPECIFIED 指定用户 */
  scope: 'ALL' | 'SEGMENT' | 'SPECIFIED'
  createdAt: string
  /** 接收人数 */
  receiverCount: number
  /** 分人群条件：卡类型ID（scope=SEGMENT 时有值） */
  cardTypeId?: number | null
  /** 分人群条件：门店ID（scope=SEGMENT 时有值） */
  storeId?: number | null
}

export interface AdminNotificationListResult {
  list: AdminNotificationBatch[]
  total: number
}

/** 新建通知请求体 */
export interface CreateNotificationPayload {
  type: string
  title: string
  content: string
  /** ALL 全部用户 / SEGMENT 分人群（按卡类型/门店筛选）/ SPECIFIED 指定用户 */
  scope: 'ALL' | 'SEGMENT' | 'SPECIFIED'
  /** 指定发送时的接收者标识（用户ID 或 手机号） */
  receivers?: string[]
  /** scope=SEGMENT：按卡类型筛选（持有该卡类型的用户） */
  cardTypeId?: number | null
  /** scope=SEGMENT：按门店筛选（持有该门店卡的用户） */
  storeId?: number | null
}

/** 群发人群预览（选择卡类型/门店时实时显示预计触达人数） */
export interface AudiencePreview {
  totalUsers: number
  /** 仅按卡类型筛选的人数 */
  cardTypeCount: number | null
  /** 仅按门店筛选的人数 */
  storeCount: number | null
  /** 组合筛选后的人数（即实际触达人数） */
  segmentCount: number
}

export interface CreateNotificationResult {
  receiverCount: number
  scope: string
  batchId: number
  missing: string[]
}

export const messageAPI = {
  /** 消息列表 */
  list(params: { page?: number; size?: number } = {}) {
    return request<MessageListResult>({
      method: 'get',
      url: '/message/list',
      params: { page: params.page || 1, size: params.size || 20 }
    })
  },

  /** 未读消息数（用于底部导航 Badge） */
  unreadCount() {
    return request<{ count: number }>({
      method: 'get',
      url: '/message/unread-count'
    })
  },

  /** 标记单条已读 */
  markRead(id: number) {
    return request<void>({
      method: 'post',
      url: `/message/${id}/read`
    })
  },

  /** 全部已读 */
  markAllRead() {
    return request<void>({
      method: 'post',
      url: '/message/read-all'
    })
  }
}

/**
 * 管理端系统通知接口
 */
export const adminNotificationAPI = {
  /** 已发通知批次列表 */
  list(params: { page?: number; size?: number; type?: string } = {}) {
    return request<AdminNotificationListResult>({
      method: 'get',
      url: '/admin/notifications',
      params: {
        page: params.page || 1,
        size: params.size || 20,
        ...(params.type ? { type: params.type } : {})
      }
    })
  },

  /** 创建并发送通知（全部用户 / 分人群 / 指定用户） */
  create(payload: CreateNotificationPayload) {
    return request<CreateNotificationResult>({
      method: 'post',
      url: '/admin/notifications',
      data: payload
    })
  },

  /** 按批次删除整组通知 */
  remove(batchId: number) {
    return request<string>({
      method: 'delete',
      url: `/admin/notifications/${batchId}`
    })
  },

  /**
   * 分人群触达预览
   * 只传 cardTypeId 或只传 storeId 均可；两者都传为交集
   */
  audiencePreview(params: { cardTypeId?: number | null; storeId?: number | null } = {}) {
    const query: Record<string, number> = {}
    if (params.cardTypeId) query.cardTypeId = params.cardTypeId
    if (params.storeId) query.storeId = params.storeId
    return request<AudiencePreview>({
      method: 'get',
      url: '/admin/notifications/audience-preview',
      params: query
    })
  }
}

export default messageAPI
