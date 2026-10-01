/**
 * 数据统计接口封装（管理端）
 * baseURL 已含 /api/v1，url 均为相对路径
 */
import service, { request } from './request'

/** 概览汇总 */
export interface OverviewStats {
  totalMembers: number
  todayNewMembers: number
  activeCards: number
  unactivatedCards: number
  expiringSoonCards: number
  todayEntries: number
  todayIncome: number
  monthIncome: number
  totalIncome: number
  paidOrders: number
  pendingOrders: number
}

/** 天趋势项（会员/核销） */
export interface TrendItem {
  date: string
  count: number
}

/** 营收趋势项 */
export interface IncomeTrendItem {
  date: string
  orders: number
  income: number
}

/** 卡类型销量项 */
export interface CardSaleItem {
  name: string
  sales: number
  income: number
}

/** 名称-数量项（门店分布等） */
export interface NameCountItem {
  name: string
  count: number
}

/** 会员卡状态项 */
export interface MembershipStatusItem {
  status: string
  count: number
}

/** 营收汇总 */
export interface IncomeSummary {
  totalIncome: number
  todayIncome: number
  monthIncome: number
  paidOrders: number
  statusList: { status: string; count: number }[]
}

/** 会员活跃概览（去重，以成功核销为活跃事件） */
export interface MemberActiveStats {
  todayActive: number
  weekActive: number
  monthActive: number
}

/** 7 日留存 cohort 单日项 */
export interface RetentionItem {
  date: string
  newUsers: number
  retainedUsers: number
  /** 当日 cohort 留存率（百分比，0-100） */
  rate: number
}

/** 7 日留存汇总 */
export interface RetentionStats {
  newCount: number
  retainedCount: number
  /** 整体留存率（百分比，0-100） */
  rate: number
  list: RetentionItem[]
}

/** 续费 / 复购统计 */
export interface RenewalStats {
  /** 近 N 天续费单数 */
  renewalOrders: number
  /** 近 N 天续费金额 */
  renewalIncome: number
  /** 近 N 天已支付订单数 */
  paidOrders: number
  /** 续费单占比（百分比，订单口径） */
  renewalRate: number
  /** 累计购卡会员数 */
  totalBuyers: number
  /** 累计复购会员数（≥2 笔支付订单） */
  repeatBuyers: number
  /** 会员复购率（百分比） */
  repeatRate: number
}

export const getOverview = () =>
  request<OverviewStats>({ method: 'get', url: '/admin/stats/overview' })

export const getMemberTrend = (days: number) =>
  request<{ list: TrendItem[] }>({ method: 'get', url: '/admin/stats/members/trend', params: { days } })

export const getMembershipStatus = () =>
  request<{ list: MembershipStatusItem[] }>({ method: 'get', url: '/admin/stats/memberships/status' })

export const getEntryTrend = (days: number) =>
  request<{ list: TrendItem[] }>({ method: 'get', url: '/admin/stats/entries/trend', params: { days } })

export const getEntryByHour = (days: number) =>
  request<{ list: { hour: number; count: number }[] }>({ method: 'get', url: '/admin/stats/entries/by-hour', params: { days } })

export const getEntryByStore = (days: number) =>
  request<{ list: NameCountItem[] }>({ method: 'get', url: '/admin/stats/entries/by-store', params: { days } })

export const getIncomeTrend = (days: number) =>
  request<{ list: IncomeTrendItem[] }>({ method: 'get', url: '/admin/stats/income/trend', params: { days } })

export const getIncomeSummary = () =>
  request<IncomeSummary>({ method: 'get', url: '/admin/stats/income/summary' })

export const getCardSales = (days: number) =>
  request<{ list: CardSaleItem[] }>({ method: 'get', url: '/admin/stats/cards/sales', params: { days } })

/** 会员活跃概览：当日 / 近 7 天 / 近 30 天去重活跃会员 */
export const getMemberActive = () =>
  request<MemberActiveStats>({ method: 'get', url: '/admin/stats/members/active' })

/** 新会员 7 日留存（仅统计已满 7 天观察期的 cohort） */
export const getMemberRetention = (days: number) =>
  request<RetentionStats>({ method: 'get', url: '/admin/stats/members/retention', params: { days } })

/** 会员卡续费 / 复购统计 */
export const getCardRenewal = (days: number) =>
  request<RenewalStats>({ method: 'get', url: '/admin/stats/cards/renewal', params: { days } })

/**
 * Excel 导出下载：以 blob 拉取并触发浏览器保存
 * （后端 Content-Disposition 已带中文名，此处 a[download] 用本地生成的文件名兜底）
 */
export async function downloadExport(url: string, params: Record<string, number> | undefined, filename: string) {
  const res = await service.get(url, {
    params,
    responseType: 'blob',
    timeout: 120000
  }) as unknown as Blob
  if (!(res instanceof Blob)) {
    throw new Error('导出响应格式异常')
  }
  const objectUrl = URL.createObjectURL(res)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
}
