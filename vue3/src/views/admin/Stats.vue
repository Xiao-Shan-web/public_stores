<template>
  <div class="stats-page">
    <!-- 标题 + 时间范围选择 -->
    <div class="page-head">
      <h2 class="page-title">数据统计</h2>
      <div class="days-picker">
        <button
          v-for="d in dayOptions"
          :key="d"
          class="day-btn"
          :class="{ active: days === d }"
          @click="setDays(d)"
        >近 {{ d }} 天</button>
      </div>
    </div>

    <!-- 概览卡片（8 个） -->
    <div class="stat-grid">
      <div class="stat-card card" v-for="s in overviewCards" :key="s.label">
        <div class="stat-icon" :style="{ background: s.bg }">
          <i class="fas" :class="s.icon" :style="{ color: s.color }"></i>
        </div>
        <div class="stat-text">
          <p class="stat-num">{{ s.value }}</p>
          <p class="stat-label">{{ s.label }}</p>
        </div>
      </div>
    </div>

    <!-- 趋势图：会员注册 / 营收 -->
    <div class="chart-grid">
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">会员注册趋势</h3>
          <span class="chart-sub">近 {{ days }} 天新增 {{ memberTotal }} 人</span>
        </div>
        <TrendChart :points="memberPoints" color="#ff6b35" :formatter="(v: number) => v + ' 人'" />
      </div>

      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">营收趋势</h3>
          <span class="chart-sub">近 {{ days }} 天收入 {{ fmtMoney(incomeSum) }} 元</span>
        </div>
        <TrendChart :points="incomePoints" color="#faad14" :formatter="(v: number) => fmtMoney(v) + ' 元'" />
      </div>
    </div>

    <!-- 核销：时段分布 / 门店分布 -->
    <div class="chart-grid">
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">核销时段分布</h3>
          <span class="chart-sub">近 {{ days }} 天成功核销</span>
        </div>
        <div class="hour-chart">
          <div class="hour-item" v-for="h in hourData" :key="h.hour">
            <div class="bar-wrap">
              <span class="bar-value" v-if="h.count > 0">{{ h.count }}</span>
              <div class="bar" :style="{ height: hourHeight(h.count) + '%' }"></div>
            </div>
            <span class="bar-label">{{ h.hour }}</span>
          </div>
        </div>
      </div>

      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">门店核销分布</h3>
          <span class="chart-sub">按持卡门店归组</span>
        </div>
        <div class="pie-wrap" v-if="storeData.length">
          <svg class="pie" viewBox="0 0 42 42">
            <circle cx="21" cy="21" r="15.9155" fill="none" stroke="#f0f0f0" stroke-width="6"></circle>
            <circle
              v-for="(p, i) in storeSegments"
              :key="i"
              cx="21" cy="21" r="15.9155"
              fill="none"
              :stroke="p.color"
              stroke-width="6"
              :stroke-dasharray="p.dash"
              :stroke-dashoffset="p.offset"
              transform="rotate(-90 21 21)"
            ></circle>
          </svg>
          <div class="pie-legend">
            <div class="legend-item" v-for="(p, i) in storeLegend" :key="i">
              <span class="legend-dot" :style="{ background: p.color }"></span>
              <span class="legend-label">{{ p.name }}</span>
              <span class="legend-value">{{ p.count }}</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">近 {{ days }} 天暂无核销数据</div>
      </div>
    </div>

    <!-- 会员活跃 / 新会员 7 日留存 -->
    <div class="chart-grid">
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">会员活跃</h3>
          <span class="chart-sub">按成功到店核销去重</span>
        </div>
        <div class="active-grid">
          <div class="active-cell">
            <p class="active-num">{{ active.todayActive }}</p>
            <p class="active-label">今日活跃</p>
          </div>
          <div class="active-cell">
            <p class="active-num">{{ active.weekActive }}</p>
            <p class="active-label">近 7 天活跃</p>
          </div>
          <div class="active-cell">
            <p class="active-num">{{ active.monthActive }}</p>
            <p class="active-label">近 30 天活跃</p>
          </div>
        </div>
        <p class="chart-foot">活跃定义：窗口期内至少 1 次成功刷脸核销</p>
      </div>

      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">新会员 7 日留存</h3>
          <span class="chart-sub">
            近 {{ days }} 天 cohort · 留存 {{ retention.retainedCount }}/{{ retention.newCount }}
          </span>
        </div>
        <div class="retention-summary">
          <span class="retention-rate">{{ retention.rate }}%</span>
          <span class="retention-desc">注册后 7 天内到店核销的新会员占比</span>
        </div>
        <TrendChart
          :points="retentionPoints"
          color="#722ed1"
          :formatter="(v: number) => v + '%'"
        />
        <p class="chart-foot">仅统计注册已满 7 天、具备完整观察期的新会员</p>
      </div>
    </div>

    <!-- 卡类型销量 / 会员卡状态 -->
    <div class="chart-grid">
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">卡类型销量排行</h3>
          <span class="chart-sub">近 {{ days }} 天已支付订单</span>
        </div>
        <div class="sales-list" v-if="cardSales.length">
          <div class="sales-item" v-for="(c, i) in cardSales" :key="i">
            <div class="sales-top">
              <span class="sales-rank" :class="'rank-' + (i < 3 ? i + 1 : 'n')">{{ i + 1 }}</span>
              <span class="sales-name">{{ c.name || '未知卡类型' }}</span>
              <span class="sales-num">{{ c.sales }} 份 · {{ fmtMoney(Number(c.income)) }} 元</span>
            </div>
            <div class="sales-track">
              <div class="sales-bar" :style="{ width: salesWidth(c.sales) + '%' }"></div>
            </div>
          </div>
        </div>
        <div v-else class="empty-hint">近 {{ days }} 天暂无售卡记录</div>
      </div>

      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">会员卡状态分布</h3>
        </div>
        <div class="status-list">
          <div class="status-item" v-for="s in membershipStatus" :key="s.status">
            <span class="result-tag" :class="statusTagClass(s.status)">{{ statusText(s.status) }}</span>
            <span class="status-count">{{ s.count }} 张</span>
          </div>
          <div v-if="!membershipStatus.length" class="empty-hint">暂无会员卡数据</div>
        </div>
      </div>
    </div>

    <!-- 续费 / 复购 -->
    <div class="card renewal-card">
      <div class="chart-head">
        <h3 class="chart-title">会员卡续费 / 复购</h3>
        <span class="chart-sub">近 {{ days }} 天续费口径 · 复购率为累计口径</span>
      </div>
      <div class="renewal-grid">
        <div class="renewal-cell">
          <p class="renewal-num">{{ renewal.renewalOrders }}</p>
          <p class="renewal-label">近 {{ days }} 天续费单</p>
        </div>
        <div class="renewal-cell">
          <p class="renewal-num">¥{{ fmtMoney(renewal.renewalIncome) }}</p>
          <p class="renewal-label">续费金额</p>
        </div>
        <div class="renewal-cell">
          <p class="renewal-num">{{ renewal.renewalRate }}%</p>
          <p class="renewal-label">续费单占比（{{ renewal.renewalOrders }}/{{ renewal.paidOrders }}）</p>
        </div>
        <div class="renewal-cell">
          <p class="renewal-num">{{ renewal.repeatRate }}%</p>
          <p class="renewal-label">会员复购率（{{ renewal.repeatBuyers }}/{{ renewal.totalBuyers }}）</p>
        </div>
      </div>
    </div>

    <!-- Excel 导出 -->
    <div class="card export-card">
      <div class="chart-head">
        <h3 class="chart-title">Excel 导出</h3>
        <span class="chart-sub">核销 / 订单按当前选择的"近 {{ days }} 天"导出</span>
      </div>
      <div class="export-btns">
        <button class="export-btn" :disabled="exporting === 'members'" @click="onExportMembers">
          <i class="fas" :class="exporting === 'members' ? 'fa-spinner fa-spin' : 'fa-file-excel'"></i>
          会员明细
        </button>
        <button class="export-btn" :disabled="exporting === 'entries'" @click="onExportEntries">
          <i class="fas" :class="exporting === 'entries' ? 'fa-spinner fa-spin' : 'fa-file-excel'"></i>
          核销记录（近 {{ days }} 天）
        </button>
        <button class="export-btn" :disabled="exporting === 'orders'" @click="onExportOrders">
          <i class="fas" :class="exporting === 'orders' ? 'fa-spinner fa-spin' : 'fa-file-excel'"></i>
          订单明细（近 {{ days }} 天）
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from '@/utils/message'
import TrendChart from '@/components/TrendChart.vue'
import {
  getOverview, getMemberTrend, getMembershipStatus,
  getEntryByHour, getEntryByStore, getIncomeTrend, getCardSales,
  getMemberActive, getMemberRetention, getCardRenewal,
  downloadExport
} from '@/api/statsAPI'
import type {
  NameCountItem, CardSaleItem, MembershipStatusItem,
  MemberActiveStats, RetentionStats, RenewalStats
} from '@/api/statsAPI'

// ==================== 时间范围 ====================
const dayOptions = [7, 30, 90]
const days = ref(30)
const setDays = (d: number) => { days.value = d }

// ==================== 概览卡片 ====================
interface OverviewCard {
  label: string
  value: string
  icon: string
  color: string
  bg: string
}
const overviewCards = ref<OverviewCard[]>([
  { label: '总会员数', value: '0', icon: 'fa-users', color: '#ff6b35', bg: 'rgba(255, 107, 53, 0.12)' },
  { label: '今日新增', value: '0', icon: 'fa-user-plus', color: '#722ed1', bg: 'rgba(114, 46, 209, 0.12)' },
  { label: '有效会员卡', value: '0', icon: 'fa-id-card', color: '#52c41a', bg: 'rgba(82, 196, 26, 0.12)' },
  { label: '7天内到期', value: '0', icon: 'fa-hourglass-half', color: '#fa541c', bg: 'rgba(250, 84, 28, 0.12)' },
  { label: '今日核销', value: '0', icon: 'fa-ticket-alt', color: '#1890ff', bg: 'rgba(24, 144, 255, 0.12)' },
  { label: '今日收入', value: '¥0', icon: 'fa-yen-sign', color: '#faad14', bg: 'rgba(250, 173, 20, 0.12)' },
  { label: '本月收入', value: '¥0', icon: 'fa-chart-line', color: '#13c2c2', bg: 'rgba(19, 194, 194, 0.12)' },
  { label: '累计收入', value: '¥0', icon: 'fa-sack-dollar', color: '#eb2f96', bg: 'rgba(235, 47, 96, 0.12)' }
])

const loadOverview = async () => {
  try {
    const res = await getOverview()
    if (res.success && res.data) {
      const d = res.data
      overviewCards.value[0].value = String(d.totalMembers ?? 0)
      overviewCards.value[1].value = String(d.todayNewMembers ?? 0)
      overviewCards.value[2].value = String(d.activeCards ?? 0)
      overviewCards.value[3].value = String(d.expiringSoonCards ?? 0)
      overviewCards.value[4].value = String(d.todayEntries ?? 0)
      overviewCards.value[5].value = '¥' + fmtMoney(d.todayIncome)
      overviewCards.value[6].value = '¥' + fmtMoney(d.monthIncome)
      overviewCards.value[7].value = '¥' + fmtMoney(d.totalIncome)
    }
  } catch { /* 静默 */ }
}

// ==================== 趋势数据（随天数变化） ====================
const memberPoints = ref<{ label: string; value: number }[]>([])
const memberTotal = computed(() => memberPoints.value.reduce((s, p) => s + p.value, 0))

const incomePoints = ref<{ label: string; value: number }[]>([])
const incomeSum = computed(() => incomePoints.value.reduce((s, p) => s + p.value, 0))

const hourData = ref<{ hour: number; count: number }[]>([])
const hourMax = computed(() => Math.max(...hourData.value.map(h => h.count), 1))
const hourHeight = (count: number) => count ? Math.max((count / hourMax.value) * 100, 4) : 0

const storeData = ref<NameCountItem[]>([])
const cardSales = ref<CardSaleItem[]>([])
const salesMax = computed(() => Math.max(...cardSales.value.map(c => c.sales), 1))
const salesWidth = (sales: number) => sales ? Math.max((sales / salesMax.value) * 100, 6) : 0

// ==================== 会员活跃 / 留存 / 续费 ====================
const active = ref<MemberActiveStats>({ todayActive: 0, weekActive: 0, monthActive: 0 })
const retention = ref<RetentionStats>({ newCount: 0, retainedCount: 0, rate: 0, list: [] })
const renewal = ref<RenewalStats>({
  renewalOrders: 0, renewalIncome: 0, paidOrders: 0, renewalRate: 0,
  totalBuyers: 0, repeatBuyers: 0, repeatRate: 0
})

const retentionPoints = computed(() =>
  retention.value.list.map(i => ({ label: i.date, value: Number(i.rate) || 0 }))
)

const loadActive = async () => {
  try {
    const res = await getMemberActive()
    if (res.success && res.data) active.value = res.data
  } catch { /* 静默 */ }
}

const loadTrendData = async () => {
  const d = days.value
  try {
    const [memberRes, incomeRes, hourRes, storeRes, salesRes, retentionRes, renewalRes] = await Promise.all([
      getMemberTrend(d), getIncomeTrend(d), getEntryByHour(d), getEntryByStore(d), getCardSales(d),
      getMemberRetention(d), getCardRenewal(d)
    ])
    if (memberRes.success && memberRes.data?.list) {
      memberPoints.value = memberRes.data.list.map(i => ({ label: i.date, value: Number(i.count) || 0 }))
    }
    if (incomeRes.success && incomeRes.data?.list) {
      incomePoints.value = incomeRes.data.list.map(i => ({ label: i.date, value: Number(i.income) || 0 }))
    }
    if (hourRes.success && hourRes.data?.list) {
      hourData.value = hourRes.data.list
    }
    if (storeRes.success && storeRes.data?.list) {
      storeData.value = storeRes.data.list
    }
    if (salesRes.success && salesRes.data?.list) {
      cardSales.value = salesRes.data.list
    }
    if (retentionRes.success && retentionRes.data) {
      retention.value = retentionRes.data
    }
    if (renewalRes.success && renewalRes.data) {
      renewal.value = renewalRes.data
    }
  } catch { /* 静默 */ }
}

watch(days, () => { loadTrendData() })

// ==================== 门店分布（SVG 环形图） ====================
const PIE_COLORS = ['#ff6b35', '#1890ff', '#52c41a', '#faad14', '#722ed1', '#eb2f96', '#13c2c2']
const storeTotal = computed(() => storeData.value.reduce((s, p) => s + p.count, 0) || 1)
const storeLegend = computed(() =>
  storeData.value.map((p, i) => ({ ...p, color: PIE_COLORS[i % PIE_COLORS.length] }))
)
const storeSegments = computed(() => {
  let acc = 0
  return storeLegend.value.map(p => {
    const percent = (p.count / storeTotal.value) * 100
    const seg = { dash: `${percent} ${100 - percent}`, offset: 25 - acc, color: p.color }
    acc += percent
    return seg
  })
})

// ==================== 会员卡状态 ====================
const membershipStatus = ref<MembershipStatusItem[]>([])
const loadMembershipStatus = async () => {
  try {
    const res = await getMembershipStatus()
    if (res.success && res.data?.list) {
      membershipStatus.value = res.data.list
    }
  } catch { /* 静默 */ }
}

const statusText = (status: string) => {
  switch (status) {
    case 'UNACTIVATED': return '未激活'
    case 'ACTIVE': return '有效'
    case 'EXPIRED': return '已过期'
    case 'DISABLED': return '已停用'
    default: return status
  }
}

const statusTagClass = (status: string) => {
  switch (status) {
    case 'ACTIVE': return 'tag-success'
    case 'UNACTIVATED': return 'tag-warn'
    case 'EXPIRED': return 'tag-muted'
    case 'DISABLED': return 'tag-fail'
    default: return 'tag-muted'
  }
}

// ==================== Excel 导出 ====================
const exporting = ref<'members' | 'entries' | 'orders' | null>(null)

const todayTag = () => new Date().toISOString().slice(0, 10)

const doExport = async (
  kind: 'members' | 'entries' | 'orders',
  url: string,
  params: Record<string, number> | undefined,
  filename: string
) => {
  if (exporting.value) return
  exporting.value = kind
  try {
    await downloadExport(url, params, filename)
    message.success('导出成功，已开始下载')
  } catch {
    message.error('导出失败，请稍后重试')
  } finally {
    exporting.value = null
  }
}

const onExportMembers = () => doExport('members', '/admin/export/members', undefined, `会员明细_${todayTag()}.xlsx`)
const onExportEntries = () => doExport('entries', '/admin/export/entries', { days: days.value }, `核销记录_${todayTag()}.xlsx`)
const onExportOrders = () => doExport('orders', '/admin/export/orders', { days: days.value }, `订单明细_${todayTag()}.xlsx`)

// ==================== 工具 ====================
const fmtMoney = (v: number | string | null | undefined) => {
  const n = Number(v ?? 0)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

onMounted(() => {
  loadOverview()
  loadMembershipStatus()
  loadActive()
  loadTrendData()
})
</script>

<style scoped>
.stats-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* 标题行 */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.page-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text);
}

.days-picker {
  display: flex;
  gap: 6px;
  background: var(--color-bg-gray, #f5f5f5);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.day-btn {
  border: none;
  background: transparent;
  padding: 6px 14px;
  font-size: 13px;
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.day-btn.active {
  background: #fff;
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

/* 概览卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon i { font-size: 20px; }

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

/* 图表区 */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-md);
}

.chart-card { display: flex; flex-direction: column; }

.chart-head {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.chart-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

.chart-sub {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* 时段柱状图（24 根） */
.hour-chart {
  display: flex;
  align-items: flex-end;
  height: 170px;
  gap: 2px;
  padding-top: 4px;
}

.hour-item {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.hour-item .bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
}

.hour-item .bar-value {
  font-size: 10px;
  color: var(--color-text-secondary);
  margin-bottom: 2px;
}

.hour-item .bar {
  width: 70%;
  min-height: 3px;
  border-radius: 3px 3px 0 0;
  background: var(--gradient-primary);
  transition: height 0.4s;
}

.hour-item .bar-label {
  font-size: 10px;
  color: var(--color-text-placeholder);
}

/* 环形图 */
.pie-wrap {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
}

.pie { width: 120px; height: 120px; flex-shrink: 0; }

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-width: 0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: 13px;
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  flex-shrink: 0;
}

.legend-label {
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.legend-value {
  margin-left: auto;
  font-weight: 600;
  color: var(--color-text);
}

/* 卡销量排行 */
.sales-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sales-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.sales-rank {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-gray, #f5f5f5);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.sales-rank.rank-1 { background: #fff1e8; color: #ff6b35; }
.sales-rank.rank-2 { background: #fff7e6; color: #faad14; }
.sales-rank.rank-3 { background: #e6f7ff; color: #1890ff; }

.sales-name {
  font-size: 14px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sales-num {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.sales-track {
  height: 8px;
  background: var(--color-bg-gray, #f5f5f5);
  border-radius: 4px;
  overflow: hidden;
}

.sales-bar {
  height: 100%;
  border-radius: 4px;
  background: var(--gradient-primary);
  transition: width 0.4s;
}

/* 卡状态列表 */
.status-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-count {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

/* 导出 */
.export-btns {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text);
  font-size: 14px;
  padding: 9px 18px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
}

.export-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.export-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.export-btn i { color: #21a353; }

/* 标签 */
.result-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
}

.tag-success { background-color: rgba(82, 196, 26, 0.12); color: var(--color-success, #52c41a); }
.tag-warn { background-color: rgba(250, 173, 20, 0.14); color: #d48806; }
.tag-fail { background-color: rgba(255, 77, 79, 0.12); color: var(--color-danger, #ff4d4f); }
.tag-muted { background-color: rgba(0, 0, 0, 0.06); color: var(--color-text-secondary); }

.empty-hint {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 28px 0;
  font-size: 13px;
}

/* 会员活跃 */
.active-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 10px;
}

.active-cell {
  background: var(--color-bg-gray, #f5f5f5);
  border-radius: var(--radius-sm);
  padding: 18px 10px;
  text-align: center;
}

.active-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.2;
}

.active-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}

.chart-foot {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 8px;
}

/* 7 日留存 */
.retention-summary {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 8px;
}

.retention-rate {
  font-size: 30px;
  font-weight: 800;
  color: #722ed1;
}

.retention-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 续费 / 复购 */
.renewal-card { padding-bottom: 18px; }

.renewal-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.renewal-cell {
  background: var(--color-bg-gray, #f5f5f5);
  border-radius: var(--radius-sm);
  padding: 18px 14px;
  text-align: center;
}

.renewal-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.2;
}

.renewal-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}

/* 响应式 */
@media (max-width: 992px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
  .renewal-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
