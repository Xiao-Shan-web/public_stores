/**
 * 订单状态公共工具
 * 集中管理订单状态映射、退款状态映射与权益期判断，
 * 供用户端（AllOrders/PaidOrders/ShippedOrders/CompletedOrders/OrderDetail）
 * 与商家端（SellerAllOrders 等 / SellerOrderDetail）共用，避免逻辑分散导致口径漂移。
 */

/** 订单状态枚举（含退款状态） */
export type OrderStatus =
  | 'PENDING'      // 待付款
  | 'PAID'         // 待发货（已付款）
  | 'PROCESSING'   // 待发货（商家处理中）
  | 'SHIPPED'      // 待收货
  | 'COMPLETED'    // 已完成
  | 'CANCELLED'    // 已取消
  | 'REFUNDING'    // 退款中
  | 'REFUNDED'     // 已退款

/** 订单状态文字映射 */
export const ORDER_STATUS_TEXT: Record<OrderStatus, string> = {
  PENDING: '待付款',
  PAID: '待发货',
  PROCESSING: '待发货',
  SHIPPED: '待收货',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

/** 订单状态样式类映射 */
export const ORDER_STATUS_CLASS: Record<OrderStatus, string> = {
  PENDING: 'status-pending',
  PAID: 'status-paid',
  PROCESSING: 'status-paid',
  SHIPPED: 'status-shipped',
  COMPLETED: 'status-completed',
  CANCELLED: 'status-cancelled',
  REFUNDING: 'status-refunding',
  REFUNDED: 'status-refunded'
}

/**
 * 退款/售后状态统一映射表。
 * refundStatus 字段所有可能取值的中文映射，
 * 用户端与商家端共用，避免 REFUNDING 等值回退为原始字符串。
 */
export const REFUND_STATUS_TEXT: Record<string, string> = {
  'PROCESSING': '处理中',
  'REFUNDING': '退款中',
  'AFTER_SALE': '售后处理中',
  'WAITING_RETURN': '待退货',
  'RETURNING': '退货中',
  'RECEIVED': '已收货',
  'SUCCESS': '已退款',
  'FAILED': '已拒绝',
  'COMPLETED': '已完成',
  'APPROVED': '已同意'
}

/**
 * 获取退款/售后状态的中文显示文本。
 * 统一处理 refundType（退款类型）与 returnStatus（退货物流状态）的优先级：
 *  1. 售后类型 + 处理中 → 售后处理中
 *  2. 退货物流状态 RETURNING / RECEIVED 优先（作为独立字段时）
 *  3. 退款状态统一映射（含 REFUNDING 退款中 / AFTER_SALE 售后处理中）
 * 用户端与商家端所有页面统一调用此方法。
 */
export function getRefundStatusText(
  status: string,
  refundType?: string,
  returnStatus?: string
): string {
  // 售后类型 + 处理中 → 售后处理中
  if (refundType === 'AFTER_SALE' && status === 'PROCESSING') {
    return '售后处理中'
  }
  // 退货物流状态优先（作为独立字段时的语义）
  if (returnStatus === 'RETURNING') return '退货中'
  if (returnStatus === 'RECEIVED') return '已收货'
  // 退款状态统一映射
  return REFUND_STATUS_TEXT[status] || status
}

/** 权益期判断所需的订单时间字段 */
export interface OrderTimeInfo {
  status?: string | null
  paidAt?: string | null
  shippedAt?: string | null
  completedAt?: string | null
}

const DAY_MS = 1000 * 60 * 60 * 24

/** 是否可申请退款：付款后 7 天内 */
export function canRefund(order: OrderTimeInfo): boolean {
  if (!order.paidAt) return false
  const days = (Date.now() - new Date(order.paidAt).getTime()) / DAY_MS
  return days <= 7
}

/**
 * 是否可申请售后：
 * - 已完成订单：完成后 7 天内
 * - 其余已发货订单：发货后 15 天内
 */
export function canAfterSale(order: OrderTimeInfo): boolean {
  if (order.status === 'COMPLETED') {
    if (!order.completedAt) return false
    const days = (Date.now() - new Date(order.completedAt).getTime()) / DAY_MS
    return days <= 7
  }
  if (!order.shippedAt) return false
  const days = (Date.now() - new Date(order.shippedAt).getTime()) / DAY_MS
  return days <= 15
}
