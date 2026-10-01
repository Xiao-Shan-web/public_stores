<template>
  <div class="buy-page page-container">
    <PageHeader title="办理会员卡" />
    <div class="page-body">
      <!-- 顶部分类 Tab -->
      <div class="bc-tabs" v-if="!loading && list.length">
        <button
          class="bc-tab"
          :class="{ active: activeTab === 'normal' }"
          @click="activeTab = 'normal'"
        >普通会员卡</button>
        <button
          class="bc-tab"
          :class="{ active: activeTab === 'pt' }"
          @click="activeTab = 'pt'"
        >私教课卡</button>
      </div>

      <p v-if="loading" class="loading-hint">加载中…</p>

      <div v-else-if="!list.length" class="empty-state">
        <i class="fas fa-id-card"></i>
        <p>暂无可办理的卡类型</p>
      </div>

      <div v-else>
        <!-- 普通会员卡 Tab -->
        <div v-if="activeTab === 'normal'">
          <div v-if="!normalCards.length" class="empty-state">
            <i class="fas fa-id-card"></i>
            <p>暂无该类型会员卡</p>
          </div>
          <div v-else class="card-list">
            <div class="bc-wrap" v-for="c in normalCards" :key="c.id">
              <div class="bc-thumb">
                <div class="bc-thumb-bg" :style="{ backgroundImage: `url(${bgUrl6})` }"></div>
                <div class="bc-thumb-overlay"></div>
                <div class="bc-thumb-content">
                  <i class="fas fa-fist-raised bc-thumb-icon"></i>
                  <p class="bc-thumb-name" :title="c.name">{{ c.name }}</p>
                </div>
              </div>
              <div class="bc-mid">
                <p class="bc-type">{{ c.name }}</p>
                <p class="bc-days">{{ c.durationDays }} 天有效</p>
                <p class="bc-scope"><i class="fas fa-store"></i>{{ scopeText(c) }}</p>
                <p class="bc-price">
                  <span class="bc-currency">¥</span><span class="bc-amount">{{ payPrice(c) }}</span>
                  <span v-if="hasActivityPrice(c)" class="bc-origin-price">¥{{ c.price }}</span>
                </p>
                <p v-if="hasActivityPrice(c)" class="bc-act-row">
                  <span class="bc-act-tag">{{ discountLabel(c.discount) }}</span>
                  <span v-if="activityEndShort(c.activityEndTime)" class="bc-act-end">
                    {{ activityEndShort(c.activityEndTime) }}
                  </span>
                </p>
              </div>
              <div class="bc-right">
                <button class="bc-btn" :disabled="purchasingId === c.id" @click="openCheckout(c)">
                  {{ purchasingId === c.id ? '支付中' : '立即办理' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 私教课卡 Tab -->
        <div v-if="activeTab === 'pt'">
          <div v-if="!ptCards.length" class="empty-state">
            <i class="fas fa-id-card"></i>
            <p>暂无该类型会员卡</p>
          </div>
          <div v-else class="card-list">
            <div class="bc-wrap pt" v-for="c in ptCards" :key="c.id">
              <div class="bc-thumb pt-thumb">
                <div class="bc-thumb-bg" :style="{ backgroundImage: `url(${bgUrl6})` }"></div>
                <div class="bc-thumb-overlay"></div>
                <div class="bc-thumb-content">
                  <i class="fas fa-dumbbell bc-thumb-icon"></i>
                  <p class="bc-thumb-name" :title="c.name">{{ c.name }}</p>
                </div>
              </div>
              <div class="bc-mid">
                <p class="bc-type">{{ c.name }}</p>
                <p class="bc-days">{{ c.totalTimes }} 节 · {{ c.durationDays }} 天有效</p>
                <p class="bc-scope"><i class="fas fa-store"></i>{{ scopeText(c) }}</p>
                <p class="bc-price">
                  <span class="bc-currency">¥</span><span class="bc-amount">{{ payPrice(c) }}</span>
                  <span v-if="hasActivityPrice(c)" class="bc-origin-price">¥{{ c.price }}</span>
                </p>
                <p v-if="hasActivityPrice(c)" class="bc-act-row">
                  <span class="bc-act-tag">{{ discountLabel(c.discount) }}</span>
                  <span v-if="activityEndShort(c.activityEndTime)" class="bc-act-end">
                    {{ activityEndShort(c.activityEndTime) }}
                  </span>
                </p>
              </div>
              <div class="bc-right">
                <button class="bc-btn pt-btn" :disabled="purchasingId === c.id" @click="openCheckout(c)">
                  {{ purchasingId === c.id ? '支付中' : '立即办理' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 结算弹层：确认订单 + 选择优惠券 ============ -->
      <div v-if="checkout" class="co-mask" @click.self="closeCheckout">
        <div class="co-modal">
          <div class="co-head">
            <span>确认订单</span>
            <i class="fas fa-times" @click="closeCheckout"></i>
          </div>
          <div class="co-body">
            <div class="co-card">
              <p class="co-name">{{ checkout.name }}</p>
              <p class="co-days" v-if="checkout.category === 'PT'">
                {{ checkout.totalTimes }} 节 · {{ checkout.durationDays }} 天有效
              </p>
              <p class="co-days" v-else>{{ checkout.durationDays }} 天有效</p>
              <div v-if="hasActivityPrice(checkout)" class="co-act-banner">
                <span class="co-act-tag">{{ discountLabel(checkout.discount) }}</span>
                <span class="co-act-title" :title="checkout.activityTitle || ''">{{ checkout.activityTitle }}</span>
                <span v-if="activityEndShort(checkout.activityEndTime)" class="co-act-end">
                  {{ activityEndShort(checkout.activityEndTime) }}
                </span>
              </div>
              <p class="co-origin">
                原价：<span :class="{ 'co-line-through': hasActivityPrice(checkout) }">¥{{ checkout.price }}</span>
              </p>
              <p v-if="hasActivityPrice(checkout)" class="co-activity-price-row">
                活动价：<em>¥{{ checkout.activityPrice }}</em>
                <small>下单自动享受，无需选择</small>
              </p>
            </div>

            <!-- 优惠券选择（不满足门槛的券置灰不可选） -->
            <p class="co-sec-title">
              优惠券
              <span class="co-sec-count" v-if="usableCount > 0">{{ usableCount }} 张可用</span>
            </p>
            <p v-if="couponsLoading" class="co-coupon-tip">优惠券加载中…</p>
            <template v-else>
              <button class="co-coupon-opt" :class="{ active: selectedCouponId === null }" @click="selectedCouponId = null">
                <span>不使用优惠券</span>
                <i v-if="selectedCouponId === null" class="fas fa-check-circle"></i>
              </button>
              <button v-for="uc in couponOptions" :key="uc.id"
                class="co-coupon-opt"
                :class="{ active: selectedCouponId === uc.id, disabled: !uc.usable }"
                :disabled="!uc.usable"
                @click="selectCoupon(uc)">
                <span>
                  <em>{{ uc.faceText || `¥${uc.amount}` }}</em>
                  {{ Number(uc.threshold) > 0 ? `满${uc.threshold}元可用` : '无门槛' }}
                  · {{ uc.couponName }}
                  <small v-if="!uc.usable" class="co-coupon-reason">{{ uc.reason || '本单不可用' }}</small>
                  <small v-else-if="Number(uc.cutAmount) > 0" class="co-coupon-cut">
                    本单可抵 ¥{{ Number(uc.cutAmount).toFixed(2) }}
                  </small>
                </span>
                <i v-if="selectedCouponId === uc.id" class="fas fa-check-circle"></i>
              </button>
              <p v-if="!couponOptions.length" class="co-coupon-tip">
                暂无可用优惠券，
                <router-link to="/user/coupon-center" class="co-link">去领券中心看看 ></router-link>
              </p>
            </template>

            <!-- 金额 -->
            <div class="co-amount-row" v-if="checkout && hasActivityPrice(checkout)">
              <span>活动优惠</span><span class="co-cut">-¥{{ activitySaving }}</span>
            </div>
            <div class="co-amount-row" v-if="selectedCoupon">
              <span>券抵扣</span><span class="co-cut">-¥{{ couponCut }}</span>
            </div>
            <div class="co-amount-row total">
              <span>实付金额</span><span class="co-pay">¥{{ payable }}</span>
            </div>

            <button class="co-confirm" :disabled="purchasingId !== null" @click="confirmPay">
              {{ purchasingId !== null ? '支付中…' : `确认支付 ¥${payable}` }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { userAPI } from '@/api/userAPI'
import { type CardType } from '@/api/adminAPI'
import { getUsableCoupons, type MyCoupon } from '@/api/operationAPI'
import { hasActivityPrice, payPrice, discountLabel, activityEndShort } from '@/utils/activityPrice'
import { useCardPurchase } from '@/composables/useCardPurchase'
import PageHeader from '@/components/PageHeader.vue'
import bgUrl6 from '@/static/images/山达健身会员卡背景6.webp'

const list = ref<CardType[]>([])
const loading = ref(false)
const activeTab = ref<'normal' | 'pt'>('normal')
const { purchasingId, payOrderFlow } = useCardPurchase()

/** 普通会员卡（月卡/季卡/半年卡/年卡） */
const normalCards = computed(() => list.value.filter(c => c.category !== 'PT'))
/** 私教课卡（10/20/30 节） */
const ptCards = computed(() => list.value.filter(c => c.category === 'PT'))

// ==================== 结算弹层（选券下单） ====================
const route = useRoute()
const checkout = ref<CardType | null>(null)
/** 全部未使用未过期券（不满足门槛的也返回，前端置灰） */
const couponOptions = ref<MyCoupon[]>([])
const couponsLoading = ref(false)
const selectedCouponId = ref<number | null>(null)

const selectedCoupon = computed(() =>
  couponOptions.value.find(uc => uc.id === selectedCouponId.value) || null
)
/** 当前订单可用（满足门槛）的券数量 */
const usableCount = computed(() => couponOptions.value.filter(uc => uc.usable).length)
/** 券抵扣金额：直接采用服务端试算结果，与下单锁定口径完全一致 */
const couponCut = computed(() => {
  if (!selectedCoupon.value) return '0.00'
  return (Number(selectedCoupon.value.cutAmount) || 0).toFixed(2)
})
/** 活动优惠金额（原价 - 活动价） */
const activitySaving = computed(() => {
  if (!checkout.value || !hasActivityPrice(checkout.value)) return '0.00'
  const saving = Number(checkout.value.price) - Number(checkout.value.activityPrice)
  return Math.max(saving, 0).toFixed(2)
})
/** 实付金额：以活动价为基数减券抵扣（最终以后端结算为准） */
const payable = computed(() => {
  if (!checkout.value) return '0.00'
  const price = Number(payPrice(checkout.value)) || 0
  return Math.max(price - Number(couponCut.value), 0).toFixed(2)
})

/** 置灰券不可选，避免用错券导致下单失败 */
const selectCoupon = (uc: MyCoupon) => {
  if (!uc.usable) return
  selectedCouponId.value = uc.id
}

const openCheckout = async (c: CardType) => {
  checkout.value = c
  selectedCouponId.value = null
  couponOptions.value = []
  couponsLoading.value = true
  try {
    // 服务端试算：券门槛按活动价校验，与下单锁定口径一致
    const res = await getUsableCoupons(payPrice(c))
    if (res.success && res.data) {
      couponOptions.value = res.data.list || []
      // 默认勾选最优券（抵扣最大）
      if (res.data.bestUserCouponId) selectedCouponId.value = res.data.bestUserCouponId
    }
  } catch { /* 拦截器已提示 */ } finally {
    couponsLoading.value = false
  }
}

const closeCheckout = () => {
  if (purchasingId.value !== null) return
  checkout.value = null
}

const confirmPay = async () => {
  if (!checkout.value) return
  await payOrderFlow(checkout.value, selectedCouponId.value)
  if (purchasingId.value === null) checkout.value = null
}

/** 适用范围展示 */
const scopeText = (c: CardType): string =>
  c.scope === 'SINGLE_STORE' ? (c.storeName || '指定门店') : '全店通用'

const loadList = async () => {
  loading.value = true
  try {
    const res = await userAPI.getCardTypes()
    if (res.success) list.value = res.data?.list || []
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    loading.value = false
  }
  // 支持从首页/活动页带卡类型直达结算（卡片类型 → 仍走选券流程，避免无券下单）
  const targetId = Number(route.query.cardTypeId)
  if (targetId) {
    const target = list.value.find(c => c.id === targetId)
    if (target) {
      activeTab.value = target.category === 'PT' ? 'pt' : 'normal'
      openCheckout(target)
    }
  }
}

onMounted(loadList)
</script>

<style scoped>
.buy-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--color-bg);
}

.page-body {
  padding: var(--spacing-md);
}

/* ==================== 顶部分类 Tab ==================== */
.bc-tabs {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.bc-tabs::-webkit-scrollbar {
  display: none;
}

.bc-tab {
  flex: 0 0 auto;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  background: #fff;
  color: var(--color-text-secondary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
  white-space: nowrap;
}

.bc-tab.active {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
}

.loading-hint {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 40px 0;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ==================== 白色大圆角卡片（三段横排） ==================== */
.bc-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background-color: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  min-height: 100px;
}

/* ==================== 左侧品牌小卡（固定 88×64） ==================== */
.bc-thumb {
  position: relative;
  flex: 0 0 88px;
  width: 88px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 3px 12px rgba(255, 107, 53, 0.25);
}

.pt-thumb {
  box-shadow: 0 3px 12px rgba(255, 158, 0, 0.3);
}

.bc-thumb-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
}

.bc-thumb-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(
    135deg,
    rgba(0, 0, 0, 0.2) 0%,
    rgba(0, 0, 0, 0.1) 40%,
    rgba(0, 0, 0, 0.45) 100%
  );
}

.bc-thumb-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.bc-thumb-icon {
  font-size: 18px;
  color: var(--color-primary);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  margin-bottom: 2px;
}

.pt-thumb .bc-thumb-icon {
  color: #ffb02e;
}

.bc-thumb-name {
  font-size: 11px;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  /* 最多 4 个字，超出省略 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 72px;
}

/* ==================== 中间卡信息（优先压缩区） ==================== */
.bc-mid {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bc-type {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
  /* 防止长名称溢出 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bc-days {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0;
}

.bc-scope {
  font-size: 11px;
  color: var(--color-text-placeholder);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.bc-price {
  display: flex;
  align-items: baseline;
  color: var(--color-primary);
  margin: 0;
}

.bc-currency {
  font-size: 12px;
  font-weight: 600;
}

.bc-amount {
  font-size: 20px;
  font-weight: 800;
  margin-left: 2px;
  line-height: 1;
}

/* 原价划线（有活动价时） */
.bc-origin-price {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-placeholder);
  text-decoration: line-through;
  align-self: center;
}

/* 活动标签行 */
.bc-act-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 2px 0 0;
}

.bc-act-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff6b35, #ff4d4f);
  line-height: 1.5;
}

.bc-act-end {
  font-size: 10px;
  color: #ff4d4f;
}

/* ==================== 右侧办理按钮（固定宽度） ==================== */
.bc-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}

.bc-btn {
  width: 84px;
  padding: 9px 0;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.3);
  transition: transform 0.15s;
  white-space: nowrap;
}

.pt-btn {
  background: linear-gradient(135deg, #ffb02e, #ff8f1f);
  box-shadow: 0 4px 12px rgba(255, 158, 0, 0.3);
}

.bc-btn:active {
  transform: scale(0.96);
}

.bc-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ==================== 空状态 ==================== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 40px var(--spacing-md);
  color: var(--color-text-secondary);
}

.empty-state > i {
  font-size: 48px;
  color: var(--color-text-placeholder);
}

/* ==================== 结算弹层 ==================== */
.co-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.co-modal {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 18px 18px 0 0;
  animation: co-slide-up 0.22s ease;
}

@keyframes co-slide-up {
  from { transform: translateY(60px); opacity: 0.4; }
  to { transform: translateY(0); opacity: 1; }
}

.co-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 18px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 16px;
  font-weight: 700;
}

.co-head i {
  color: #999;
  cursor: pointer;
  padding: 4px;
}

.co-body {
  padding: 14px 18px 18px;
  max-height: 66vh;
  overflow-y: auto;
}

.co-card {
  background: var(--color-bg, #fafafa);
  border-radius: 10px;
  padding: 12px 14px;
}

.co-name {
  font-size: 15px;
  font-weight: 700;
  margin: 0;
}

.co-days {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin: 4px 0 0;
}

.co-origin {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 8px 0 0;
}

.co-origin span {
  font-weight: 700;
  color: var(--color-text);
}

.co-origin span.co-line-through {
  font-weight: 400;
  color: var(--color-text-placeholder);
  text-decoration: line-through;
}

/* 结算弹层活动信息 */
.co-act-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 77, 79, 0.08);
  font-size: 12px;
}

.co-act-tag {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff6b35, #ff4d4f);
}

.co-act-title {
  flex: 1 1 auto;
  min-width: 0;
  color: #ff4d4f;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.co-act-end {
  flex-shrink: 0;
  color: var(--color-text-placeholder);
  font-size: 11px;
}

.co-activity-price-row {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 4px 0 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.co-activity-price-row em {
  font-style: normal;
  font-size: 17px;
  font-weight: 800;
  color: #ff4d4f;
}

.co-activity-price-row small {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.co-sec-title {
  font-size: 13.5px;
  font-weight: 600;
  margin: 16px 0 8px;
}

.co-coupon-tip {
  font-size: 12.5px;
  color: var(--color-text-placeholder);
  margin: 0;
  padding: 8px 0;
}

.co-link {
  color: var(--color-primary);
  text-decoration: none;
}

.co-coupon-opt {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 10px;
  background: #fff;
  padding: 10px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-text);
  text-align: left;
  cursor: pointer;
}

.co-coupon-opt em {
  font-style: normal;
  font-weight: 700;
  color: var(--color-primary);
  margin-right: 4px;
}

.co-coupon-opt.active {
  border-color: var(--color-primary);
  background: rgba(255, 107, 53, 0.06);
}

/* 不满足门槛的券：置灰不可点 */
.co-coupon-opt.disabled {
  opacity: 0.5;
  background: #fafafa;
  border-style: dashed;
  cursor: not-allowed;
}

.co-coupon-opt.disabled em {
  color: var(--color-text-placeholder);
}

.co-coupon-reason {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--color-text-placeholder);
}

.co-coupon-cut {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--color-primary);
}

.co-sec-count {
  font-size: 11.5px;
  font-weight: 400;
  color: var(--color-text-placeholder);
  margin-left: 4px;
}

.co-coupon-opt .fa-check-circle {
  color: var(--color-primary);
  flex-shrink: 0;
}

.co-amount-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13.5px;
  color: var(--color-text-secondary);
  padding: 6px 0;
}

.co-amount-row.total {
  border-top: 1px dashed #eee;
  margin-top: 6px;
  padding-top: 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.co-cut {
  color: var(--color-primary);
}

.co-pay {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-primary);
}

.co-confirm {
  width: 100%;
  border: none;
  margin-top: 14px;
  padding: 12px 0;
  border-radius: 24px;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.co-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
