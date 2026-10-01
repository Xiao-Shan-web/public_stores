/**
 * 运营能力接口封装（优惠券 / 限时活动）
 * baseURL 已含 /api/v1，故 url 均为相对路径
 */
import { request } from './request'

// ==================== 类型定义 ====================

/** 券模板 */
export interface CouponTemplate {
  id: number
  name: string
  /** FULL_REDUCE-满减，DIRECT-直减，DISCOUNT-折扣 */
  type: string
  threshold: string
  /** 抵扣面额（满减/直减券）；折扣券为 0 */
  amount: string
  /** 折扣率（DISCOUNT，0.85 = 85 折） */
  discount?: string | null
  /** 最高优惠上限（DISCOUNT，null 不封顶） */
  maxDiscount?: string | null
  /** 面额展示文案（后端下发，折扣券为「8.5折」） */
  faceText?: string
  /** 发行总量（0 表示不限量） */
  totalCount: number
  issuedCount: number
  perUserLimit: number
  /** FIXED-固定区间，DAYS_AFTER_RECEIVE-领取后N天 */
  validType: string
  startTime: string | null
  endTime: string | null
  validDays: number | null
  /** 1-上架，0-下架 */
  status: number
  createdAt?: string
}

/** 券统计行（管理端列表） */
export interface CouponStat {
  couponId: number
  name: string
  totalCount: number
  issuedCount: number
  usedCount: number
}

/** 领券中心条目 */
export interface CouponCenterItem extends CouponTemplate {
  ownedCount: number
  receivable: boolean
  /** 剩余数量（-1 表示不限量） */
  remainCount: number
}

/** 我的券 */
export interface MyCoupon {
  id: number
  couponId: number
  couponName: string
  couponType: string
  threshold: string
  amount: string
  /** 折扣率（DISCOUNT） */
  discount?: string | null
  /** 最高优惠上限（DISCOUNT） */
  maxDiscount?: string | null
  /** 面额展示文案（后端下发） */
  faceText?: string
  /** 结算试算：本单是否满足门槛可用 */
  usable?: boolean
  /** 结算试算：本单可抵扣金额（与后端下单口径一致） */
  cutAmount?: string
  /** 结算试算：不可用原因（置灰提示） */
  reason?: string | null
  /** UNUSED-可用 / LOCKED-已占用 / USED-已使用 / EXPIRED-已过期 */
  status: string
  orderId: number | null
  /** 占用来源订单号（status=LOCKED 时有值） */
  orderNo?: string | null
  /** 占用来源订单状态（PENDING/PAID/CANCELLED/REFUNDED） */
  orderStatus?: string | null
  startTime: string
  endTime: string
  usedAt: string | null
  createdAt: string
  expired: boolean
}

/** 我的券筛选范围 */
export type MyCouponScope = 'UNUSED' | 'LOCKED' | 'USED' | 'EXPIRED' | 'ALL'

/** 限时活动 */
export interface ActivityItem {
  id: number
  title: string
  subtitle: string | null
  coverUrl: string | null
  content: string | null
  cardTypeId: number
  discount: string
  quotaTotal: number | null
  quotaUsed: number
  startTime: string
  endTime: string
  cardTypeName?: string | null
  originalPrice?: string | null
  activityPrice?: string | null
  /** 管理端：ONGOING 进行中 / UPCOMING 未开始 / ENDED 已结束 */
  phase?: string
  status?: number
  /** 管理端：用户端当前是否可见（1-可见 0-不可见；上架+时间窗内+名额未满） */
  userVisible?: number
  isDeleted?: number
}

/** 卡类型选项（活动/群发下拉用） */
export interface CardTypeOption {
  id: number
  name: string
  price?: string
  category?: string
}

// ==================== 用户端：优惠券 ====================

/** 领券中心 */
export const getCouponCenter = () =>
  request<{ list: CouponCenterItem[]; usableCount: number }>({
    method: 'get',
    url: '/user/coupons/center'
  })

/** 领取优惠券 */
export const receiveCoupon = (id: number) =>
  request<{ userCouponId: number; name: string; amount: string; endTime: string }>({
    method: 'post',
    url: `/user/coupons/${id}/receive`
  })

/** 我的优惠券：scope=UNUSED/LOCKED/USED/EXPIRED/ALL */
export const getMyCoupons = (scope: MyCouponScope = 'UNUSED') =>
  request<{ list: MyCoupon[]; usableCount: number }>({
    method: 'get',
    url: '/user/coupons',
    params: { scope }
  })

/** 取消占用：释放被未支付订单锁定的券（自动取消该待支付订单并退回券） */
export const releaseMyCoupon = (id: number) =>
  request<{ status: string; usableCount: number }>({
    method: 'post',
    url: `/user/coupons/${id}/release`
  })

/** 结算试算：返回全部未使用券（含可用标记/本单可抵扣金额/置灰原因）+ 最优券 */
export const getUsableCoupons = (amount: number | string) =>
  request<{
    list: MyCoupon[]
    usableCount: number
    bestUserCouponId: number | null
    bestDiscount: string
  }>({
    method: 'get',
    url: '/user/coupons/usable',
    params: { amount }
  })

// ==================== 用户端：活动 ====================

/** 进行中的活动列表 */
export const getOngoingActivities = () =>
  request<{ list: ActivityItem[] }>({ method: 'get', url: '/user/activities' })

/** 活动详情 */
export const getActivityDetail = (id: number) =>
  request<ActivityItem>({ method: 'get', url: `/user/activities/${id}` })

// ==================== 管理端：优惠券 ====================

/** 券列表（含统计） */
export const adminGetCoupons = () =>
  request<{ list: CouponStat[]; templates: CouponTemplate[] }>({
    method: 'get',
    url: '/admin/coupons'
  })

export interface CouponSaveParams {
  name: string
  /** FULL_REDUCE / DIRECT / DISCOUNT */
  type: string
  threshold: number | string
  amount: number | string
  /** 折扣率（DISCOUNT，0.85 = 85 折） */
  discount?: number | string | null
  /** 最高优惠上限（DISCOUNT，可空） */
  maxDiscount?: number | string | null
  totalCount: number
  perUserLimit: number
  validType: string
  validDays?: number | null
  startTime?: string | null
  endTime?: string | null
  status: number
}

/** 新建券 */
export const adminCreateCoupon = (data: CouponSaveParams) =>
  request<{ id: number }>({ method: 'post', url: '/admin/coupons', data })

/** 编辑券 */
export const adminUpdateCoupon = (id: number, data: CouponSaveParams) =>
  request({ method: 'put', url: `/admin/coupons/${id}`, data })

/** 券上下架 */
export const adminToggleCoupon = (id: number, status: number) =>
  request({ method: 'put', url: `/admin/coupons/${id}/status`, data: { status } })

/** 删除券 */
export const adminDeleteCoupon = (id: number) =>
  request({ method: 'delete', url: `/admin/coupons/${id}` })

// ==================== 管理端：活动 ====================

/** 活动列表 */
export const adminGetActivities = () =>
  request<{ list: ActivityItem[] }>({ method: 'get', url: '/admin/activities' })

export interface ActivitySaveParams {
  title: string
  subtitle?: string | null
  coverUrl?: string | null
  content?: string | null
  cardTypeId: number
  discount: number | string
  quotaTotal?: number | null
  startTime: string
  endTime: string
  status: number
}

/** 新建活动 */
export const adminCreateActivity = (data: ActivitySaveParams) =>
  request<{ id: number }>({ method: 'post', url: '/admin/activities', data })

/** 编辑活动 */
export const adminUpdateActivity = (id: number, data: ActivitySaveParams) =>
  request({ method: 'put', url: `/admin/activities/${id}`, data })

/** 活动上下架 */
export const adminToggleActivity = (id: number, status: number) =>
  request({ method: 'put', url: `/admin/activities/${id}/status`, data: { status } })

/** 删除活动 */
export const adminDeleteActivity = (id: number) =>
  request({ method: 'delete', url: `/admin/activities/${id}` })

// ==================== 管理端：群发人群预览 ====================

/** 群发人群预览 */
export const adminAudiencePreview = (params: { cardTypeId?: number; storeId?: number }) =>
  request<{ totalUsers: number; cardTypeCount: number | null; storeCount: number | null; segmentCount?: number }>({
    method: 'get',
    url: '/admin/notifications/audience-preview',
    params
  })

export default {
  getCouponCenter,
  receiveCoupon,
  getMyCoupons,
  releaseMyCoupon,
  getUsableCoupons,
  getOngoingActivities,
  getActivityDetail,
  adminGetCoupons,
  adminCreateCoupon,
  adminUpdateCoupon,
  adminToggleCoupon,
  adminDeleteCoupon,
  adminGetActivities,
  adminCreateActivity,
  adminUpdateActivity,
  adminToggleActivity,
  adminDeleteActivity,
  adminAudiencePreview
}
