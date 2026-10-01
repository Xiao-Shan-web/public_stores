<template>
  <div class="coupon-center page-container">
    <PageHeader title="领券中心" />

    <div class="page-body">
      <!-- 分段 Tab（指示条平移动效） -->
      <div class="seg-tabs">
        <span class="seg-indicator" :style="indicatorStyle"></span>
        <button
          class="seg-tab"
          :class="{ active: tabIndex === 0 }"
          @click="switchTab('center')"
        >可领取</button>
        <button
          class="seg-tab"
          :class="{ active: tabIndex === 1 }"
          @click="switchTab('mine')"
        >我的券<span v-if="usableCount > 0" class="tab-badge">{{ usableCount }}</span></button>
      </div>

      <!-- 双面板滑动容器（内容跟随平移，支持左右滑动切换） -->
      <div
        ref="swipeEl"
        class="swipe"
        @touchstart="onTouchStart"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
        @touchcancel="onTouchEnd"
      >
        <div class="swipe-track" :style="trackStyle">
          <!-- 面板1：领券中心 -->
          <div class="swipe-panel">
            <!-- 空态/加载占位：列表为空时常驻容器，仅切换文案，避免增删节点造成高度跳动 -->
            <div v-if="!centerList.length" class="empty-state">
              <template v-if="loading">
                <i class="fas fa-spinner fa-spin"></i>
                <p>加载中…</p>
              </template>
              <template v-else>
                <i class="fas fa-ticket-alt"></i>
                <p>暂无可领取的优惠券</p>
              </template>
            </div>
            <div v-else class="coupon-list">
              <div class="cp-item" v-for="c in centerList" :key="c.id" :class="{ used: !c.receivable }">
                <div class="cp-left">
                  <p v-if="isDiscount(c)" class="cp-amount"><span class="yuan"></span>{{ foldText(c.discount) }}</p>
                  <p v-else class="cp-amount"><span class="yuan">¥</span>{{ fmtAmount(c.amount) }}</p>
                  <p class="cp-cond">
                    {{ isDiscount(c) && Number(c.maxDiscount) > 0 ? `最高减${fmtAmount(c.maxDiscount)}` : '' }}
                    {{ Number(c.threshold) > 0 ? `满${fmtAmount(c.threshold)}可用` : '无门槛' }}
                  </p>
                </div>
                <div class="cp-mid">
                  <p class="cp-name">{{ c.name }}</p>
                  <p class="cp-valid">
                    <i class="far fa-clock"></i>
                    {{ c.validType === 'FIXED'
                      ? `${shortDate(c.startTime)} ~ ${shortDate(c.endTime)}`
                      : `领取后 ${c.validDays ?? 30} 天内有效` }}
                  </p>
                  <p class="cp-remain" v-if="c.remainCount >= 0">
                    剩余 {{ c.remainCount }} 张 · 每人限领 {{ c.perUserLimit }} 张
                  </p>
                  <p class="cp-remain" v-else>不限量 · 每人限领 {{ c.perUserLimit }} 张</p>
                </div>
                <div class="cp-right">
                  <button
                    class="cp-btn"
                    :class="{ done: !c.receivable }"
                    :disabled="!c.receivable || receivingId === c.id"
                    @click="receive(c)"
                  >
                    {{ !c.receivable ? '已领取' : (receivingId === c.id ? '领取中' : '立即领取') }}
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 面板2：我的券 -->
          <div class="swipe-panel">
            <div class="sub-tabs">
              <button
                v-for="s in mineScopes"
                :key="s.value"
                class="sub-tab"
                :class="{ active: mineScope === s.value }"
                @click="switchMineScope(s.value)"
              >{{ s.label }}</button>
            </div>

            <div v-if="!mineList.length" class="empty-state">
              <template v-if="loading">
                <i class="fas fa-spinner fa-spin"></i>
                <p>加载中…</p>
              </template>
              <template v-else>
                <i class="fas fa-ticket-alt"></i>
                <p>暂无{{ mineScope === 'UNUSED' ? '可用' : mineScope === 'LOCKED' ? '占用中' : mineScope === 'USED' ? '已使用' : mineScope === 'EXPIRED' ? '已过期' : '' }}优惠券</p>
              </template>
            </div>
            <div v-else class="coupon-list">
              <div
                class="cp-item mine"
                v-for="m in mineList"
                :key="m.id"
                :class="{ disabled: m.status !== 'UNUSED' || m.expired }"
              >
                <div class="cp-left">
                  <p v-if="isDiscount(m)" class="cp-amount">{{ foldText(m.discount) }}</p>
                  <p v-else class="cp-amount"><span class="yuan">¥</span>{{ fmtAmount(m.amount) }}</p>
                  <p class="cp-cond">
                    {{ isDiscount(m) && Number(m.maxDiscount) > 0 ? `最高减${fmtAmount(m.maxDiscount)}` : '' }}
                    {{ Number(m.threshold) > 0 ? `满${fmtAmount(m.threshold)}可用` : '无门槛' }}
                  </p>
                </div>
                <div class="cp-mid">
                  <p class="cp-name">{{ m.couponName || '优惠券' }}</p>
                  <p class="cp-valid">
                    <i class="far fa-clock"></i> 有效期至 {{ shortDate(m.endTime) }}
                  </p>
                  <p class="cp-state-tip" v-if="stateTip(m)">{{ stateTip(m) }}</p>
                </div>
                <div class="cp-right">
                  <button
                    v-if="m.status === 'UNUSED' && !m.expired"
                    class="cp-btn use-btn"
                    @click="goBuyCards"
                  >去使用</button>
                  <button
                    v-else-if="m.status === 'LOCKED' && !m.expired"
                    class="cp-btn release-btn"
                    :disabled="releasingId === m.id"
                    @click="releaseLocked(m)"
                  >{{ releasingId === m.id ? '处理中' : '取消占用' }}</button>
                  <span v-else class="cp-stamp">{{ stampText(m) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { message } from '@/utils/message'
import { getCouponCenter, receiveCoupon, getMyCoupons, releaseMyCoupon } from '@/api/operationAPI'
import type { CouponCenterItem, MyCoupon, MyCouponScope } from '@/api/operationAPI'

const router = useRouter()

const tab = ref<'center' | 'mine'>('center')
const loading = ref(false)
const receivingId = ref<number | null>(null)
const releasingId = ref<number | null>(null)
const usableCount = ref(0)

const centerList = ref<CouponCenterItem[]>([])
const mineScope = ref<MyCouponScope>('UNUSED')
/** 多 scope 缓存：切换子筛选时直接命中旧数据，不闪不白 */
const mineCache = ref<Record<string, MyCoupon[]>>({
  UNUSED: [],
  LOCKED: [],
  USED: [],
  EXPIRED: [],
  ALL: []
})
/** 当前 mineList 从缓存读取，不独立维护 */
const mineList = computed(() => mineCache.value[mineScope.value] || [])

const mineScopes = [
  { value: 'UNUSED' as const, label: '可用' },
  { value: 'LOCKED' as const, label: '占用中' },
  { value: 'USED' as const, label: '已使用' },
  { value: 'EXPIRED' as const, label: '已过期' },
  { value: 'ALL' as const, label: '全部' }
]

const loadCenter = async () => {
  // 仅首次加载显示 loading；已有列表时不切换 loading，避免已渲染内容闪动
  const firstLoad = !centerList.value.length
  if (firstLoad) loading.value = true
  try {
    const res = await getCouponCenter()
    if (res.success && res.data) {
      centerList.value = res.data.list || []
      usableCount.value = res.data.usableCount || 0
    }
  } catch { /* 拦截器已提示 */ } finally {
    if (firstLoad) loading.value = false
  }
}

const loadMine = async () => {
  const cached = mineCache.value[mineScope.value]
  const firstLoad = !cached || !cached.length
  if (firstLoad) loading.value = true
  try {
    const res = await getMyCoupons(mineScope.value)
    if (res.success && res.data) {
      mineCache.value[mineScope.value] = res.data.list || []
      usableCount.value = res.data.usableCount || 0
    }
  } catch { /* 拦截器已提示 */ } finally {
    if (firstLoad) loading.value = false
  }
}

const switchTab = (t: 'center' | 'mine') => {
  if (tab.value === t) return
  tab.value = t
  if (t === 'center') loadCenter()
  else loadMine()
}

// ==================== Tab 滑动切换动效（仅交互层，不改动数据逻辑） ====================

const swipeEl = ref<HTMLElement | null>(null)
const tabIndex = computed(() => (tab.value === 'center' ? 0 : 1))

const dragging = ref(false)
const dragX = ref(0)
let touchStartX = 0
let touchStartY = 0
let lockDir: 'h' | 'v' | null = null

/** Tab 指示条平移 */
const indicatorStyle = computed(() => ({
  transform: `translateX(${tabIndex.value * 100}%)`
}))

/** 内容面板平移：激活页 -100%*index + 手势拖拽偏移；拖拽时关闭过渡跟手，松手后带缓动回弹 */
const trackStyle = computed(() => ({
  transform: `translate3d(calc(${-tabIndex.value * 100}% + ${dragX.value}px), 0, 0)`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)'
}))

/** 边缘橡皮筋阻尼：第一页向右拖 / 第二页向左拖时衰减为 1/3 */
const rubber = (dx: number) =>
  (tabIndex.value === 0 && dx > 0) || (tabIndex.value === 1 && dx < 0) ? dx / 3 : dx

const onTouchStart = (e: TouchEvent) => {
  const t = e.touches[0]
  touchStartX = t.clientX
  touchStartY = t.clientY
  lockDir = null
  dragging.value = true
  dragX.value = 0
}

const onTouchMove = (e: TouchEvent) => {
  if (!dragging.value) return
  const t = e.touches[0]
  const dx = t.clientX - touchStartX
  const dy = t.clientY - touchStartY
  // 方向锁定：首次超过 8px 判定横向/纵向，纵向放行页面滚动
  if (!lockDir) {
    if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
    lockDir = Math.abs(dx) > Math.abs(dy) ? 'h' : 'v'
  }
  if (lockDir === 'h') {
    if (e.cancelable) e.preventDefault()
    dragX.value = rubber(dx)
  }
}

const onTouchEnd = () => {
  if (lockDir === 'h' && swipeEl.value && Math.abs(dragX.value) > swipeEl.value.offsetWidth * 0.25) {
    switchTab(tabIndex.value === 0 ? 'mine' : 'center')
  }
  dragging.value = false
  dragX.value = 0
  lockDir = null
}

const switchMineScope = (s: MyCouponScope) => {
  if (mineScope.value === s) return
  mineScope.value = s
  loadMine()
}

/** 取消占用：释放被未支付订单锁定的券（后端会同步取消该待支付订单） */
const releaseLocked = async (m: MyCoupon) => {
  if (releasingId.value !== null) return
  releasingId.value = m.id
  try {
    const res = await releaseMyCoupon(m.id)
    if (res.success) {
      message.success(res.data?.status === 'EXPIRED' ? '已取消占用，该券已过期' : '已取消占用，优惠券已退回')
      // 本地更新：从「占用中」缓存移除；其他 scope 缓存置空待重新拉取（券状态已变）
      if (typeof res.data?.usableCount === 'number') usableCount.value = res.data.usableCount
      mineCache.value.LOCKED = (mineCache.value.LOCKED || []).filter(item => item.id !== m.id)
      mineCache.value.UNUSED = []
      mineCache.value.USED = []
      mineCache.value.EXPIRED = []
      mineCache.value.ALL = []
    }
  } catch { /* 拦截器已提示 */ } finally {
    releasingId.value = null
  }
}

/** 折扣券判定与折扣文案（8.5折） */
const isDiscount = (c: { type?: string; couponType?: string }) =>
  (c.type || c.couponType) === 'DISCOUNT'

const foldText = (discount?: string | null) => {
  const rate = Number(discount)
  if (!rate || rate <= 0 || rate >= 1) return '折扣'
  return `${(rate * 10).toFixed(1).replace(/\.0$/, '')}折`
}

const receive = async (c: CouponCenterItem) => {
  if (receivingId.value !== null) return
  receivingId.value = c.id
  try {
    const res = await receiveCoupon(c.id)
    if (res.success) {
      message.success('领取成功，可在「我的券」查看')
      // 本地单券更新：不重新请求列表，避免整页闪动
      c.receivable = false
      if (c.remainCount > 0) c.remainCount -= 1
      usableCount.value += 1
      // 领取后「我的券」数据已变化，使相关缓存失效（下次切入时重新拉取）
      mineCache.value.UNUSED = []
      mineCache.value.ALL = []
    }
  } catch { /* 拦截器已提示 */ } finally {
    receivingId.value = null
  }
}

const goBuyCards = () => {
  router.push('/user/buy-cards')
}

const stateTip = (m: MyCoupon) => {
  if (m.status === 'LOCKED') {
    const order = m.orderNo ? `订单 ${m.orderNo}` : ''
    if (m.expired) return `占用中 · 已过期${order ? '（' + order + '）' : ''}`
    return `下单未支付占用${order ? ' · ' + order : ''}`
  }
  if (m.expired) return '已过期'
  if (m.status === 'USED') return m.usedAt ? `使用时间 ${m.usedAt.replace('T', ' ').substring(0, 16)}` : '已使用'
  if (m.status === 'EXPIRED') return '已过期'
  return ''
}

const stampText = (m: MyCoupon) => {
  if (m.status === 'USED') return '已使用'
  if (m.expired || m.status === 'EXPIRED') return '已过期'
  if (m.status === 'LOCKED') return '占用中'
  return ''
}

const fmtAmount = (v: string | number) => {
  const n = Number(v ?? 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

const shortDate = (t: string | null) => (t ? t.replace('T', ' ').substring(0, 10) : '-')

onMounted(() => { loadCenter() })
</script>

<style scoped>
.coupon-center { min-height: 100vh; background: var(--color-bg); }

/* 分段 Tab */
.seg-tabs {
  display: flex;
  gap: 0;
  background: var(--color-bg-gray, #f0f0f0);
  border-radius: 20px;
  padding: 3px;
  margin-bottom: var(--spacing-md);
  position: relative;
}

/* 滑动指示条 */
.seg-indicator {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc(50% - 3px);
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  will-change: transform;
}

.seg-tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 8px 0;
  border-radius: 18px;
  font-size: 14px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.3s;
  position: relative;
  z-index: 1;
}

.seg-tab.active {
  color: var(--color-primary);
  font-weight: 600;
}

/* 双面板滑动容器：横向裁切，纵向滚动交给浏览器 */
.swipe {
  overflow: hidden;
  touch-action: pan-y;
}

.swipe-track {
  display: flex;
  will-change: transform;
}

.swipe-panel {
  width: 100%;
  flex-shrink: 0;
}

.tab-badge {
  display: inline-block;
  margin-left: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--color-danger, #ff4d4f);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  vertical-align: middle;
}

/* 子筛选 */
.sub-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: var(--spacing-sm);
}

.sub-tab {
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text-secondary);
  padding: 5px 14px;
  border-radius: 15px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.sub-tab.active {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: rgba(255, 107, 53, 0.07);
  font-weight: 600;
}

/* 券列表 */
.coupon-list { display: flex; flex-direction: column; gap: 12px; }

.cp-item {
  display: flex;
  align-items: stretch;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.05);
  position: relative;
}

/* 左侧金额区（渐变+锯齿分隔） */
.cp-left {
  width: 104px;
  flex-shrink: 0;
  background: var(--gradient-primary);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 14px 6px;
  position: relative;
}

.cp-left::after {
  content: '';
  position: absolute;
  right: -6px;
  top: 0;
  bottom: 0;
  width: 12px;
  background: radial-gradient(circle at 6px 6px, transparent 5px, #fff 5px) repeat-y;
  background-size: 12px 14px;
}

.cp-amount {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}

.cp-amount .yuan { font-size: 14px; margin-right: 1px; }

.cp-cond {
  font-size: 11px;
  margin-top: 6px;
  opacity: 0.92;
}

.cp-mid {
  flex: 1;
  min-width: 0;
  padding: 14px 12px 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  justify-content: center;
}

.cp-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp-valid {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.cp-remain {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.cp-state-tip {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.cp-right {
  display: flex;
  align-items: center;
  padding: 0 14px;
  flex-shrink: 0;
}

.cp-btn {
  border: none;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  padding: 8px 16px;
  border-radius: 16px;
  cursor: pointer;
  white-space: nowrap;
}

.cp-btn.done {
  background: var(--color-bg-gray, #f0f0f0);
  color: var(--color-text-placeholder);
}

.cp-btn:disabled { cursor: not-allowed; }

.use-btn {
  background: #fff;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
}

.release-btn {
  background: #fff;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.release-btn:disabled { opacity: 0.6; }

.cp-stamp {
  font-size: 12px;
  color: var(--color-text-placeholder);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 4px 10px;
  white-space: nowrap;
}

/* 失效态（灰度） */
.cp-item.disabled { opacity: 0.62; }
.cp-item.disabled .cp-left {
  background: linear-gradient(135deg, #b9b9b9, #9a9a9a);
}
.cp-item.used { opacity: 0.7; }
</style>
