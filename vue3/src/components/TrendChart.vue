<template>
  <div class="trend-chart" :style="{ height: height + 'px' }">
    <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" :stop-color="color" stop-opacity="0.28"></stop>
          <stop offset="100%" :stop-color="color" stop-opacity="0.02"></stop>
        </linearGradient>
      </defs>

      <!-- 网格线 + Y 轴刻度 -->
      <g v-for="(gy, i) in gridYs" :key="'g' + i">
        <line :x1="PAD_L" :y1="gy" :x2="W - PAD_R" :y2="gy" stroke="var(--color-border, #eee)" stroke-width="1" stroke-dasharray="4 4"></line>
        <text :x="PAD_L - 8" :y="gy + 4" text-anchor="end" class="axis-text">{{ fmtShort(maxValue - (maxValue / 4) * i) }}</text>
      </g>

      <!-- 面积 + 折线 -->
      <path v-if="areaPath" :d="areaPath" :fill="`url(#${uid})`"></path>
      <path v-if="linePath" :d="linePath" fill="none" :stroke="color" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round"></path>

      <!-- 数据点（hover 显示原生 tooltip） -->
      <circle
        v-for="(p, i) in points"
        :key="'p' + i"
        :cx="px(i)"
        :cy="py(p.value)"
        r="2.6"
        :fill="color"
        class="dot"
      >
        <title>{{ p.label }}：{{ formatter ? formatter(p.value) : p.value }}</title>
      </circle>

      <!-- X 轴标签（最多展示 7 个） -->
      <text
        v-for="(p, i) in points"
        :key="'x' + i"
        v-show="showXIndex(i)"
        :x="px(i)"
        :y="H - 6"
        text-anchor="middle"
        class="axis-text"
      >{{ p.label }}</text>

      <!-- 空数据 -->
      <text v-if="!points.length" :x="W / 2" :y="H / 2" text-anchor="middle" class="empty-text">暂无数据</text>
    </svg>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 通用 SVG 趋势折线图（无第三方依赖）
 * - 面积渐变 + 折线 + 数据点原生 tooltip
 * - Y 轴自动取整到 1/2/5×10^n，X 轴最多显示 7 个标签
 */
interface Point {
  label: string
  value: number
}

const props = withDefaults(defineProps<{
  points: Point[]
  color?: string
  height?: number
  /** 数值展示格式化（tooltip 用） */
  formatter?: (v: number) => string
}>(), {
  color: '#ff6b35',
  height: 220,
  formatter: undefined
})

// 画布尺寸（viewBox，随容器等比缩放）
const W = 640
const H = 240
const PAD_L = 52
const PAD_R = 14
const PAD_T = 14
const PAD_B = 26

// 渐变 id 唯一化（避免多实例互相覆盖）
const uid = `tc-${Math.random().toString(36).slice(2, 9)}`

const maxValue = computed(() => {
  const max = Math.max(...props.points.map(p => p.value), 0)
  return niceCeil(max || 1)
})

/** 向上取整到 1/2/5×10^n，让刻度好看 */
const niceCeil = (v: number) => {
  const exp = Math.floor(Math.log10(v))
  const base = Math.pow(10, exp)
  const n = v / base
  const nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10
  return nice * base
}

const gridYs = computed(() => {
  const ys: number[] = []
  for (let i = 0; i <= 4; i++) {
    ys.push(PAD_T + ((H - PAD_T - PAD_B) / 4) * i)
  }
  return ys
})

const plotW = W - PAD_L - PAD_R
const plotH = H - PAD_T - PAD_B

const px = (i: number) => {
  const n = props.points.length
  if (n <= 1) return PAD_L + plotW / 2
  return PAD_L + (plotW / (n - 1)) * i
}

const py = (v: number) => {
  const ratio = Math.min(v / maxValue.value, 1)
  return PAD_T + plotH * (1 - ratio)
}

const linePath = computed(() => {
  if (!props.points.length) return ''
  return props.points.map((p, i) => `${i === 0 ? 'M' : 'L'}${px(i).toFixed(1)},${py(p.value).toFixed(1)}`).join(' ')
})

const areaPath = computed(() => {
  if (!props.points.length) return ''
  const first = `${px(0).toFixed(1)},${(PAD_T + plotH).toFixed(1)}`
  const last = `${px(props.points.length - 1).toFixed(1)},${(PAD_T + plotH).toFixed(1)}`
  return `${linePath.value} L${last} L${first} Z`
})

/** X 轴标签最多 7 个：按步长抽稀 */
const showXIndex = (i: number) => {
  const n = props.points.length
  if (n <= 7) return true
  const step = Math.ceil(n / 7)
  if (i === 0) return true
  if (i === n - 1) return true
  return i % step === 0 && i <= n - 1 - step / 2
}

/** Y 轴刻度缩写：1.2w / 3.5k / 890 */
const fmtShort = (v: number) => {
  if (v >= 10000) return `${(v / 10000).toFixed(v % 10000 === 0 ? 0 : 1)}w`
  if (v >= 1000) return `${(v / 1000).toFixed(v % 1000 === 0 ? 0 : 1)}k`
  return `${Math.round(v)}`
}
</script>

<style scoped>
.trend-chart {
  width: 100%;
}

.trend-chart svg {
  width: 100%;
  height: 100%;
  display: block;
}

.axis-text {
  font-size: 11px;
  fill: var(--color-text-placeholder, #999);
}

.dot {
  opacity: 0.85;
}

.empty-text {
  font-size: 13px;
  fill: var(--color-text-placeholder, #999);
}
</style>
