/**
 * 限时活动价展示工具（首页热门卡 / 办卡列表 / 结算弹层共用）
 *
 * 约定：
 * - 卡类型 price 始终为原价；存在进行中活动时后端额外下发 activityPrice
 * - 是否有活动以"活动价确实低于原价"为准，折扣 = 1 时不展示活动样式
 * - 最终下单/支付金额由服务端按下单时点重算，这里的结果仅用于展示
 */
import type { CardType } from '@/api/adminAPI'

/** 当前是否存在可享受的活动价 */
export const hasActivityPrice = (c: CardType): boolean => {
  const activity = Number(c.activityPrice)
  const original = Number(c.price)
  return !!c.activityPrice && Number.isFinite(activity) && Number.isFinite(original) && activity < original
}

/** 用户实际应付卡价：有活动价用活动价，否则原价 */
export const payPrice = (c: CardType): string =>
  hasActivityPrice(c) ? (c.activityPrice as string) : c.price

/**
 * 折扣文案：0.8 → "8 折"，0.85 → "8.5 折"，0.75 → "7.5 折"
 * 用折扣值直算，不用"活动价/原价"反推，避免四舍五入产生 7.99 折
 */
export const discountLabel = (discount?: string | null): string => {
  const d = Number(discount)
  if (!Number.isFinite(d) || d <= 0) return '限时优惠'
  const zhe = Math.round(d * 100) / 10
  return `限时 ${zhe} 折`
}

/** 活动截止日期短文案：2026-09-30T... → "9-30 截止" */
export const activityEndShort = (endTime?: string | null): string => {
  if (!endTime) return ''
  const date = new Date(endTime)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getMonth() + 1}-${date.getDate()} 截止`
}
