/**
 * 管理端接口封装
 * baseURL 已含 /api/v1，故此处的 url 均为相对路径
 */
import { request } from './request'

/** 卡类型 */
export interface CardType {
  id: number
  name: string
  /** 分类：NORMAL-普通会员卡，PT-私教会员卡 */
  category: string
  /** 适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店 */
  scope: string
  /** 绑定门店ID（scope=SINGLE_STORE 时有值） */
  storeId: number | null
  /** 绑定门店名称（JOIN 查询返回） */
  storeName?: string | null
  durationDays: number
  /** 私教课总节数（PT 有值，NORMAL 为 null） */
  totalTimes: number | null
  /** 价格（字符串形式，避免 JS 精度问题）；有进行中活动时该值仍为原价，活动价见 activityPrice */
  price: string
  description?: string
  /** 是否启用：1-启用，0-禁用 */
  isActive: number
  createdAt?: string
  updatedAt?: string
  /** 用户端实时活动字段（管理端列表不返回；无进行中活动时为 null） */
  /** 原价（等同 price） */
  originalPrice?: string
  /** 活动价（原价 × 折扣，已四舍五入两位；无活动为 null） */
  activityPrice?: string | null
  /** 命中的活动 ID */
  activityId?: number | null
  /** 活动标题 */
  activityTitle?: string | null
  /** 活动结束时间（ISO 字符串） */
  activityEndTime?: string | null
  /** 活动折扣（如 "0.8" 表示 8 折） */
  discount?: string | null
}

export interface CardTypeListResult {
  list: CardType[]
}

export interface CardTypeSaveParams {
  name: string
  /** 分类：NORMAL（默认）或 PT */
  category?: string
  /** 适用范围：ALL_STORE（默认）/ SINGLE_STORE */
  scope?: string
  /** 绑定门店ID（scope=SINGLE_STORE 时必填） */
  storeId?: number | null
  durationDays: number
  /** 私教课总节数（PT 必填正整数，NORMAL 不传或 null） */
  totalTimes?: number | null
  price: number | string
  description?: string
  isActive: number
}

/** 会员卡记录（管理端） */
export interface MembershipRecord {
  id: number
  userId: number
  phone: string
  cardNo: string
  cardType: string
  cardTypeId: number | null
  /** 购卡门店快照ID（全店通用为 null） */
  storeId: number | null
  cardTypeName: string | null
  /** 分类：NORMAL/PT（JOIN card_types，历史卡为 null 按 NORMAL 处理） */
  category: string | null
  /** 适用范围：ALL_STORE/SINGLE_STORE */
  scope: string | null
  /** 门店名称（JOIN stores） */
  storeName: string | null
  totalTimes: number | null
  remainingTimes: number | null
  /** 激活时间 = 生效时间（start_time）；未激活为 null */
  startTime: string | null
  endTime: string | null
  status: string
  createdAt: string
  updatedAt?: string
}

export interface MembershipListResult {
  list: MembershipRecord[]
  total: number
}

/** 会员卡管理端筛选参数 */
export interface MembershipFilter {
  status?: string
  storeId?: number | null
  cardTypeId?: number | null
  phone?: string
  /** 卡号尾号（与 phone 为 OR 关系，前端单搜索框同时下发） */
  cardNo?: string
  page?: number
  size?: number
}

/** 会员卡操作记录（来自管理员审计日志） */
export interface MembershipLog {
  id: number
  adminId: number | null
  username: string | null
  module: string
  /** HTTP 方法：POST/PUT/DELETE/PATCH */
  action: string
  method: string
  /** 请求 URI（含会员卡ID） */
  uri: string
  /** 参数摘要（含原因/天数/次数等，已脱敏） */
  paramSummary: string | null
  ip: string | null
  /** SUCCESS/FAIL */
  result: string
  costMs: number
  createdAt: string
}

/** 会员卡详情（完整信息 + 操作记录） */
export interface MembershipDetail {
  detail: MembershipRecord
  logs: MembershipLog[]
}

/** 控制台统计 */
export interface DashboardStats {
  totalMembers: number
  todayNewMembers: number
  activeCards: number
  todayEntries: number
  monthIncome: number
}

/** 刷脸核销结果 */
export interface EntryVerifyResult {
  userId: number
  membershipId: number
  cardNo: string
  cardName: string
  /** 卡种分类：NORMAL/PT */
  category?: string
  /** 适用范围：ALL_STORE/SINGLE_STORE */
  scope?: string
  storeName?: string | null
  status: string
  remainingTimes: number | null
  startTime: string | null
  endTime: string | null
  entryRecordId: number
  entryTime: string
  /** 核销结果：SUCCESS-入场成功，FAILED-入场失败；前端核销页据此判定，与记录列表同源 */
  result: 'SUCCESS' | 'FAILED'
}

/** 卡类型列表 */
export const getCardTypes = () =>
  request<CardTypeListResult>({ method: 'get', url: '/admin/card-types' })

/** 新建卡类型 */
export const createCardType = (data: CardTypeSaveParams) =>
  request<CardType>({ method: 'post', url: '/admin/card-types', data })

/** 编辑卡类型 */
export const updateCardType = (id: number, data: CardTypeSaveParams) =>
  request<CardType>({ method: 'put', url: `/admin/card-types/${id}`, data })

/** 删除卡类型（软删除） */
export const deleteCardType = (id: number) =>
  request({ method: 'delete', url: `/admin/card-types/${id}` })

/** 启用 / 禁用卡类型（status: 1-启用，0-禁用） */
export const toggleCardTypeStatus = (id: number, status: number) =>
  request({ method: 'put', url: `/admin/card-types/${id}/status`, data: { status } })

/** 控制台统计 */
export const getDashboardStats = () =>
  request<DashboardStats>({ method: 'get', url: '/admin/dashboard/stats' })

/** 会员卡记录列表（管理端，支持状态/门店/卡种/手机号筛选） */
export const getMemberships = (params?: MembershipFilter) =>
  request<MembershipListResult>({ method: 'get', url: '/admin/memberships', params })

/** 会员卡详情（完整信息 + 操作记录） */
export const getMembershipDetail = (id: number) =>
  request<MembershipDetail>({ method: 'get', url: `/admin/memberships/${id}` })

/** 手动停用异常会员卡（reason 停用原因，记录到操作日志） */
export const disableMembership = (id: number, reason?: string) =>
  request<{ id: number; status: string }>({
    method: 'put',
    url: `/admin/memberships/${id}/disable`,
    params: reason ? { reason } : undefined
  })

/** 管理员手动激活会员卡（UNACTIVATED → ACTIVE，无需刷脸） */
export const activateMembership = (id: number) =>
  request<{
    membershipId: number
    cardName: string | null
    startTime: string
    endTime: string
    durationDays: number | null
  }>({ method: 'put', url: `/admin/memberships/${id}/activate` })

/** 手动延期（days 延期天数；EXPIRED 卡延期后恢复生效） */
export const extendMembership = (id: number, days: number, reason?: string) =>
  request<{ id: number; endTime: string; status: string }>({
    method: 'put',
    url: `/admin/memberships/${id}/extend`,
    params: { days, ...(reason ? { reason } : {}) }
  })

/** 手动调整剩余次数（value 新剩余次数，仅次卡） */
export const adjustMembershipTimes = (id: number, value: number, reason?: string) =>
  request<{ id: number; remainingTimes: number }>({
    method: 'put',
    url: `/admin/memberships/${id}/times`,
    params: { value, ...(reason ? { reason } : {}) }
  })

/** 刷脸核销（门店端） */
export const entryVerify = (imageBase64: string) =>
  request<EntryVerifyResult>({ method: 'post', url: '/admin/entry/verify', data: { imageBase64 } })

/** 会员卡明细导出（复用 statsAPI 的 downloadExport，支持与列表同口径筛选） */
export function exportMembershipsUrl(filter: MembershipFilter) {
  const params = new URLSearchParams()
  if (filter.status) params.set('status', filter.status)
  if (filter.storeId) params.set('storeId', String(filter.storeId))
  if (filter.cardTypeId) params.set('cardTypeId', String(filter.cardTypeId))
  if (filter.phone) params.set('phone', filter.phone)
  if (filter.cardNo) params.set('cardNo', filter.cardNo)
  const qs = params.toString()
  return `/admin/export/memberships${qs ? '?' + qs : ''}`
}

export default {
  getCardTypes,
  createCardType,
  updateCardType,
  deleteCardType,
  toggleCardTypeStatus,
  getDashboardStats,
  getMemberships,
  getMembershipDetail,
  disableMembership,
  activateMembership,
  extendMembership,
  adjustMembershipTimes,
  exportMembershipsUrl,
  entryVerify
}
