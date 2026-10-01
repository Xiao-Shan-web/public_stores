<template>
  <div class="dashboard">
    <!-- 统计卡片（4 个横排） -->
    <div class="stat-grid">
      <div class="stat-card card" v-for="s in stats" :key="s.label">
        <div class="stat-icon" :style="{ background: s.bg }">
          <i class="fas" :class="s.icon" :style="{ color: s.color }"></i>
        </div>
        <div class="stat-text">
          <p class="stat-num">{{ s.value }}</p>
          <p class="stat-label">{{ s.label }}</p>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <div class="chart-grid">
      <!-- 近 7 天核销趋势（柱状图） -->
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">近 7 天核销趋势</h3>
        </div>
        <div class="bar-chart">
          <div class="bar-item" v-for="(b, i) in trend" :key="i">
            <div class="bar-wrap">
              <span class="bar-value">{{ b.count }}</span>
              <div class="bar" :style="{ height: barHeight(b.count) + '%', background: 'var(--gradient-primary)' }"></div>
            </div>
            <span class="bar-label">{{ b.date }}</span>
          </div>
        </div>
      </div>

      <!-- 会员卡类型占比（饼图） -->
      <div class="chart-card card">
        <div class="chart-head">
          <h3 class="chart-title">会员卡类型占比</h3>
        </div>
        <div class="pie-wrap">
          <svg class="pie" viewBox="0 0 42 42">
            <circle cx="21" cy="21" r="15.9155" fill="none" stroke="#f0f0f0" stroke-width="6"></circle>
            <circle
              v-for="(p, i) in pieSegments"
              :key="i"
              cx="21"
              cy="21"
              r="15.9155"
              fill="none"
              :stroke="p.color"
              stroke-width="6"
              :stroke-dasharray="p.dash"
              :stroke-dashoffset="p.offset"
              transform="rotate(-90 21 21)"
            ></circle>
          </svg>
          <div class="pie-legend">
            <div class="legend-item" v-for="(p, i) in pieData" :key="i">
              <span class="legend-dot" :style="{ background: p.color }"></span>
              <span class="legend-label">{{ p.label }}</span>
              <span class="legend-value">{{ p.count }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 最近核销记录 -->
    <div class="card">
      <div class="chart-head">
        <h3 class="chart-title">最近核销记录</h3>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>会员手机号</th>
              <th>卡号</th>
              <th>卡名称</th>
              <th>核销时间</th>
              <th>核销结果</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in recentRecords" :key="r.id">
              <td>{{ r.phone || '未识别会员' }}</td>
              <td>{{ r.cardNo || '-' }}</td>
              <td>{{ r.cardName || '-' }}</td>
              <td>{{ formatMin(r.useTime) }}</td>
              <td>
                <span class="result-tag" :class="r.result === 'SUCCESS' ? 'tag-success' : 'tag-fail'">
                  {{ r.result === 'SUCCESS' ? '成功' : '失败' }}
                </span>
              </td>
            </tr>
            <tr v-if="!recentRecords.length">
              <td colspan="5" class="empty-row">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { request } from '@/api/request'

// 统计卡片
const stats = ref([
  { label: '总会员数', value: 0, icon: 'fa-users', color: '#ff6b35', bg: 'rgba(255, 107, 53, 0.12)' },
  { label: '今日新增', value: 0, icon: 'fa-user-plus', color: '#722ed1', bg: 'rgba(114, 46, 209, 0.12)' },
  { label: '有效会员卡', value: 0, icon: 'fa-id-card', color: '#52c41a', bg: 'rgba(82, 196, 26, 0.12)' },
  { label: '今日核销', value: 0, icon: 'fa-ticket-alt', color: '#1890ff', bg: 'rgba(24, 144, 255, 0.12)' },
  { label: '本月收入', value: 0, icon: 'fa-yen-sign', color: '#faad14', bg: 'rgba(250, 173, 20, 0.12)' }
])

// 近 7 天核销趋势
const trend = ref<{ date: string; count: number }[]>([])

// 柱状图最大值（用于计算高度）
const maxCount = computed(() => {
  if (!trend.value.length) return 1
  return Math.max(...trend.value.map((t) => t.count), 1)
})

const barHeight = (count: number) => {
  if (!count) return 0
  return Math.max((count / maxCount.value) * 100, 4)
}

// 卡类型占比（饼图）
const pieData = ref<{ label: string; count: number; color: string }[]>([])
const pieTotal = computed(() =>
  pieData.value.reduce((s, p) => s + p.count, 0) || 1
)

// 计算 SVG 饼图分段：周长 100，dasharray = "占比 剩余"，offset 累加
const pieSegments = computed(() => {
  let acc = 0
  return pieData.value.map((p) => {
    const percent = (p.count / pieTotal.value) * 100
    const seg = {
      dash: `${percent} ${100 - percent}`,
      offset: 25 - acc, // 起点从顶部（25 = 1/4 周长）
      color: p.color
    }
    acc += percent
    return seg
  })
})

// 最近核销记录（最新 5 条）
interface RecentRecord {
  id: number
  phone: string | null
  cardNo: string | null
  cardName: string | null
  useTime: string
  result: 'SUCCESS' | 'FAILED'
}
const recentRecords = ref<RecentRecord[]>([])

// 列表时间展示到分钟：2026-09-09 09:56
const formatMin = (t: string) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

// 加载统计卡片
const loadStats = async () => {
  try {
    const res = await request<{
      totalMembers: number
      todayNewMembers: number
      activeCards: number
      todayEntries: number
      monthIncome: number
    }>({ method: 'get', url: '/admin/dashboard/stats' })
    if (res.success && res.data) {
      stats.value[0].value = res.data.totalMembers || 0
      stats.value[1].value = res.data.todayNewMembers || 0
      stats.value[2].value = res.data.activeCards || 0
      stats.value[3].value = res.data.todayEntries || 0
      stats.value[4].value = res.data.monthIncome || 0
    }
  } catch (e) {
    /* 后端接口未就绪时保持 0 */
  }
}

// 加载近 7 天趋势
const loadTrend = async () => {
  try {
    const res = await request<{ list: { date: string; count: number }[] }>({
      method: 'get',
      url: '/admin/dashboard/trend'
    })
    if (res.success && res.data?.list) {
      trend.value = res.data.list
    }
  } catch (e) {
    /* 静默 */
  }
}

// 加载卡类型分布
const loadPie = async () => {
  const colors = ['#ff6b35', '#52c41a', '#1890ff', '#faad14']
  try {
    const res = await request<{ list: { type: string; count: number }[] }>({
      method: 'get',
      url: '/admin/dashboard/card-distribution'
    })
    if (res.success && res.data?.list) {
      pieData.value = res.data.list.map((p, i) => ({
        label: pieLabel(p.type),
        count: p.count,
        color: colors[i % colors.length]
      }))
    }
  } catch (e) {
    /* 静默 */
  }
}

const pieLabel = (type: string) => {
  switch (type) {
    case 'TIMES': return '次卡'
    case 'MONTHLY': return '月卡'
    case 'YEARLY': return '年卡'
    default: return type
  }
}

// 加载最近核销记录
const loadRecent = async () => {
  try {
    const res = await request<{ list: RecentRecord[] }>({
      method: 'get',
      url: '/admin/records',
      params: { limit: 5 }
    })
    if (res.success && res.data?.list) {
      recentRecords.value = res.data.list.slice(0, 5)
    }
  } catch (e) {
    /* 静默 */
  }
}

// 非关键请求延后到浏览器空闲期（不支持时退化为短延时），避免首屏 4 个接口同时争抢
const onIdle = (cb: () => void) => {
  const w = window as any
  if (typeof w.requestIdleCallback === 'function') {
    w.requestIdleCallback(cb, { timeout: 1200 })
  } else {
    setTimeout(cb, 200)
  }
}

onMounted(() => {
  // 顶部统计卡片是首屏核心信息：立即加载
  loadStats()
  // 趋势图 / 卡类型占比 / 最近核销记录：非关键，延后到空闲期再请求，先让框架与卡片渲染
  onIdle(() => {
    loadTrend()
    loadPie()
    loadRecent()
  })
})
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* 统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
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

.stat-icon i {
  font-size: 20px;
}

.stat-num {
  font-size: 24px;
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
  grid-template-columns: 1.6fr 1fr;
  gap: var(--spacing-md);
}

.chart-card {
  display: flex;
  flex-direction: column;
}

.chart-head {
  margin-bottom: var(--spacing-sm);
}

.chart-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
}

/* 柱状图 */
.bar-chart {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 160px;
  padding: var(--spacing-xs) 0;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  height: 100%;
  gap: 4px;
}

.bar-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  width: 100%;
  position: relative;
}

.bar-value {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.bar {
  width: 60%;
  max-width: 36px;
  min-height: 4px;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  transition: height 0.4s;
}

.bar-label {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

/* 饼图 */
.pie-wrap {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.pie {
  width: 110px;
  height: 110px;
  flex-shrink: 0;
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
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
}

.legend-value {
  margin-left: auto;
  font-weight: 600;
  color: var(--color-text);
}

/* 数据表格 */
.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th,
.data-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.data-table th {
  color: var(--color-text-secondary);
  font-weight: 600;
  background-color: var(--color-bg);
}

.empty-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 20px 0;
}

.result-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-success {
  background-color: rgba(82, 196, 26, 0.12);
  color: var(--color-success);
}

.tag-fail {
  background-color: rgba(255, 77, 79, 0.12);
  color: var(--color-danger);
}

/* 响应式：小屏统计卡片改为 2 列 */
@media (max-width: 992px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
