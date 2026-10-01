/**
 * 用户端业务接口
 */
import { request } from './request'
import { type CardType } from './adminAPI'

export interface MemberCard {
  id: number
  cardNo: string
  cardName: string
  /** 状态码：0-未激活，1-有效，2-已过期，3-已停用 */
  status: number
  /** 状态文本（未激活/有效/已过期/已停用） */
  statusText?: string
  /** 分类：NORMAL-普通卡，PT-私教课卡（历史卡缺省按 NORMAL） */
  category?: string
  /** 适用范围：ALL_STORE-全店通用，SINGLE_STORE-指定单店 */
  scope?: string
  /** 门店名称（SINGLE_STORE 时有值） */
  storeName?: string | null
  /** 总节数（私教课卡；普通卡为 null） */
  totalTimes: number | null
  /** 剩余节数（私教课卡；普通卡为 null） */
  remainTimes: number | null
  /** 剩余天数（按天卡，由后端计算；次卡为 null） */
  remainingDays?: number | null
  expireTime: string
  /** 激活时间 = 生效时间（start_time，激活时写入）；未激活为 null，前端显示"未激活" */
  startTime?: string | null
  createTime: string
}

export interface EntryRecord {
  id: number
  /** 卡名称 */
  cardName: string
  /** 核销时间（yyyy-MM-dd HH:mm:ss） */
  useTime: string
  /** 核销结果：SUCCESS-入场成功，FAILED-入场失败 */
  result: 'SUCCESS' | 'FAILED'
  /** 失败原因（result=FAILED 时有值） */
  failReason?: string | null
}

export interface OrderItem {
  id: number
  orderNo: string
  cardType: string
  cardTypeId: number
  amount: string
  /** 原价（活动价前） */
  originalAmount?: string | null
  /** 参与的活动ID */
  activityId?: number | null
  /** 使用的用户券ID */
  userCouponId?: number | null
  /** 券抵扣金额 */
  discountAmount?: string
  /** 后端返回的卡类型名称（下单响应） */
  cardTypeName?: string
  /** PENDING-待支付 / PAID-已支付 / CANCELLED-已取消 / REFUNDED-已退款 */
  status: string
  payTime?: string
  membershipId?: number
  createdAt?: string
}

export interface OrderListResult {
  list: OrderItem[]
  total: number
}

/** 性别枚举：MALE-男，FEMALE-女，UNKNOWN-保密 */
export type Gender = 'MALE' | 'FEMALE' | 'UNKNOWN'

/** 用户个人资料 */
export interface UserProfile {
  userId: number
  phone?: string
  avatar: string | null
  /** 头像缩略图（200x200，列表展示用） */
  avatarThumb?: string | null
  nickname: string | null
  realName: string | null
  gender: Gender
  /** 生日（yyyy-MM-dd） */
  birthday: string | null
  bio: string | null
  createTime?: string
}

export interface PayResultData {
  orderNo: string
  status?: string
  membershipId?: number
  /** 支付宝支付表单 HTML（alipay 模式返回，兜底字段） */
  payFormHtml?: string
  /** 支付宝跳转地址（alipay 模式返回，前端直接 window.location.href） */
  payUrl?: string
  /** 支付模式：mock / alipay */
  payMode?: string
}

export const userAPI = {
  /** 获取当前用户的会员卡（最新一张，首页 Hero 用） */
  getCard() {
    return request<MemberCard>({
      method: 'get',
      url: '/user/card'
    })
  },

  /** 获取当前用户全部会员卡（我的会员卡页用，按状态优先级排序） */
  getMemberships() {
    return request<MemberCard[]>({
      method: 'get',
      url: '/user/memberships'
    })
  },

  /** 获取核销记录列表 */
  getRecords(params?: { page?: number; size?: number }) {
    return request<{ list: EntryRecord[]; total: number }>({
      method: 'get',
      url: '/user/records',
      params
    })
  },

  /** 获取用户个人资料 */
  getProfile() {
    return request<UserProfile>({
      method: 'get',
      url: '/user/profile'
    })
  },

  /** 更新用户个人资料（昵称/真实姓名/性别/生日/简介） */
  updateProfile(data: {
    nickname?: string | null
    realName?: string | null
    gender: Gender
    birthday?: string | null
    bio?: string | null
  }) {
    return request<UserProfile>({
      method: 'put',
      url: '/user/profile',
      data
    })
  },

  /** 上传头像（multipart/form-data），返回主图与缩略图地址并已落库 */
  uploadAvatar(file: File | Blob) {
    const formData = new FormData()
    formData.append('file', file)
    return request<{ avatar: string; avatarThumb: string }>({
      method: 'post',
      url: '/user/profile/avatar',
      data: formData
      // 不显式设置 Content-Type，由浏览器自动生成 multipart boundary
    })
  },

  /** 用户端可用卡类型列表（仅启用） */
  getCardTypes() {
    return request<{ list: CardType[] }>({
      method: 'get',
      url: '/user/card-types'
    })
  },

  /** 创建订单（可传 userCouponId 使用优惠券；金额由服务端按活动价与券重算） */
  createOrder(cardTypeId: number, userCouponId?: number | null) {
    return request<OrderItem>({
      method: 'post',
      url: '/user/orders',
      data: { cardTypeId, userCouponId: userCouponId ?? null }
    })
  },

  /** 取消订单（PENDING 可取消，已锁定券自动释放） */
  cancelOrder(orderNo: string) {
    return request<string>({
      method: 'post',
      url: `/user/orders/${orderNo}/cancel`
    })
  },

  /** 订单退款（仅 PAID 可退；会员卡停用，优惠券按有效期退回或作废） */
  refundOrder(orderNo: string) {
    return request<string>({
      method: 'post',
      url: `/user/orders/${orderNo}/refund`
    })
  },

  /** 模拟支付（dev 直接成功；真实 Alipay 后改为拉起收银台） */
  payOrder(orderNo: string) {
    return request<PayResultData>({
      method: 'post',
      url: `/user/orders/${orderNo}/pay`
    })
  },

  /** 订单详情 */
  getOrder(orderNo: string) {
    return request<OrderItem>({
      method: 'get',
      url: `/user/orders/${orderNo}`
    })
  },

  /** 我的订单列表 */
  getOrders(params?: { page?: number; size?: number }) {
    return request<OrderListResult>({
      method: 'get',
      url: '/user/orders',
      params
    })
  },

  /**
   * 人脸录入状态
   * registered：系统里有人脸登记；
   * matchable：当前识别模式下底库确实能比对上（切换 face.mode 后旧登记会为 false）；
   * hint：registered 但 !matchable 时给出的重新录入提示。
   */
  getFaceStatus() {
    return request<{
      registered: boolean
      matchable: boolean
      faceToken: string | null
      hint?: string | null
    }>({
      method: 'get',
      url: '/user/face/status'
    })
  },

  /** 注销人脸（同时清理识别底库，避免底库残留可匹配的人脸） */
  unregisterFace() {
    return request<{ registered: boolean; galleryCleared: boolean; message?: string }>({
      method: 'delete',
      url: '/user/face'
    })
  },

  /** 人脸录入（独立模块，不再自动激活会员卡） */
  registerFace(imageBase64: string) {
    return request<{
      faceToken: string
      registered: boolean
    }>({
      method: 'post',
      url: '/user/face/register',
      data: { imageBase64 }
    })
  },

  /** 激活指定会员卡（UNACTIVATED → ACTIVE，与录入人脸解耦） */
  activateMembership(id: number) {
    return request<{
      membershipId: number
      cardName: string | null
      startTime: string
      endTime: string
      durationDays: number | null
    }>({
      method: 'post',
      url: `/user/memberships/${id}/activate`
    })
  }
}

export default userAPI
