<template>
  <div class="home-page page-container">
    <!-- 顶部用户条：头像 + 昵称（点击进入"我的"） -->
    <div class="home-user-bar" @click="goProfile">
      <!-- 头像点击放大预览，阻止冒泡避免触发整行跳转"我的" -->
      <div class="hub-avatar" @click.stop>
        <UserAvatar :src="homeAvatar" previewable />
      </div>
      <div class="hub-text">
        <p class="hub-name">{{ homeName }}</p>
      </div>
      <i class="fas fa-chevron-right hub-arrow"></i>
    </div>

    <!-- 品牌会员卡 Hero：仅做品牌展示与跳转引导，不展示真实卡数据、不承载激活等业务 -->
    <section class="hero-card">
      <!-- 骨架屏占位层：图片加载完淡出，避免空白 -->
      <div v-if="!heroLoaded" class="hero-skeleton"></div>
      <!-- 背景图层：品牌底图 -->
      <div class="hero-bg" :class="{ 'is-loaded': heroLoaded }" :style="{ backgroundImage: `url(${cardBgUrl})` }"></div>
      <!-- 遮罩层（保证文字可读） -->
      <div class="hero-overlay"></div>

      <div class="hero-content center">
        <p class="brand-name">山达健身</p>
        <p class="hero-main-text">{{ heroGuide }}</p>
        <button class="hero-btn" @click="onHeroBtnClick">{{ heroBtnText }}</button>
      </div>
    </section>

    <!-- 小欢迎语 -->
    <p class="greeting-line">{{ greeting }}，燃动起来！</p>

    <!-- 快捷入口 -->
    <section class="quick-grid">
      <div class="quick-item" @click="goAiPlan">
        <i class="fas fa-robot"></i>
        <span>AI饮食计划</span>
      </div>
      <div class="quick-item" @click="goCard">
        <i class="fas fa-id-card"></i>
        <span>我的会员卡</span>
      </div>
      <div class="quick-item" @click="goRecords">
        <i class="fas fa-history"></i>
        <span>入场核销</span>
      </div>
      <div class="quick-item" @click="goMyShare">
        <i class="fas fa-share-alt"></i>
        <span>我的分享</span>
      </div>
    </section>

    <!-- 热门会员卡：普通卡 + 私教课卡各取前 2 张，卡片样式与 BuyCards.vue 一致 -->
    <!-- 显示条件只看数据本身（有可购卡才渲染）；未登录时 cardTypes 不会加载，自然不渲染 -->
    <section class="cards-section" v-if="homeNormalCards.length || homePtCards.length">
      <div class="section-head">
        <h3 class="section-title">热门会员卡</h3>
        <span class="more" @click="goBuyCards">查看全部 ></span>
      </div>

      <!-- 普通会员卡 -->
      <section class="hc-group" v-if="homeNormalCards.length">
        <h4 class="hc-group-title">普通会员卡</h4>
        <div class="hc-list">
          <div class="hc-wrap" v-for="c in homeNormalCards" :key="c.id">
            <div class="hc-thumb">
              <div class="hc-thumb-bg" :class="{ 'is-loaded': thumbLoaded }" :style="{ backgroundImage: `url(${cardBgUrl6})` }"></div>
              <div class="hc-thumb-overlay"></div>
              <div class="hc-thumb-content">
                <i class="fas fa-fist-raised hc-thumb-icon"></i>
                <p class="hc-thumb-name" :title="c.name">{{ c.name }}</p>
              </div>
            </div>
            <div class="hc-mid">
              <p class="hc-type">{{ c.name }}</p>
              <p class="hc-days">{{ c.durationDays }} 天有效</p>
              <p class="hc-scope"><i class="fas fa-store"></i>{{ scopeText(c) }}</p>
              <p class="hc-price">
                <span class="hc-currency">¥</span><span class="hc-amount">{{ payPrice(c) }}</span>
                <span v-if="hasActivityPrice(c)" class="hc-origin-price">¥{{ c.price }}</span>
              </p>
              <p v-if="hasActivityPrice(c)" class="hc-act-row">
                <span class="hc-act-tag">{{ discountLabel(c.discount) }}</span>
              </p>
            </div>
            <div class="hc-right">
              <button class="hc-btn" :disabled="purchasingId === c.id" @click="handleBuy(c)">
                {{ purchasingId === c.id ? '支付中' : '立即办理' }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 私教课卡 -->
      <section class="hc-group" v-if="homePtCards.length">
        <h4 class="hc-group-title">私教课卡</h4>
        <div class="hc-list">
          <div class="hc-wrap pt" v-for="c in homePtCards" :key="c.id">
            <div class="hc-thumb pt-thumb">
              <div class="hc-thumb-bg" :class="{ 'is-loaded': thumbLoaded }" :style="{ backgroundImage: `url(${cardBgUrl6})` }"></div>
              <div class="hc-thumb-overlay"></div>
              <div class="hc-thumb-content">
                <i class="fas fa-dumbbell hc-thumb-icon"></i>
                <p class="hc-thumb-name" :title="c.name">{{ c.name }}</p>
              </div>
            </div>
            <div class="hc-mid">
              <p class="hc-type">{{ c.name }}</p>
              <p class="hc-days">{{ c.totalTimes }} 节 · {{ c.durationDays }} 天有效</p>
              <p class="hc-scope"><i class="fas fa-store"></i>{{ scopeText(c) }}</p>
              <p class="hc-price">
                <span class="hc-currency">¥</span><span class="hc-amount">{{ payPrice(c) }}</span>
                <span v-if="hasActivityPrice(c)" class="hc-origin-price">¥{{ c.price }}</span>
              </p>
              <p v-if="hasActivityPrice(c)" class="hc-act-row">
                <span class="hc-act-tag">{{ discountLabel(c.discount) }}</span>
              </p>
            </div>
            <div class="hc-right">
              <button class="hc-btn pt-btn" :disabled="purchasingId === c.id" @click="handleBuy(c)">
                {{ purchasingId === c.id ? '支付中' : '立即办理' }}
              </button>
            </div>
          </div>
        </div>
      </section>
    </section>

    <!-- 今日推荐 -->
    <section class="hot-section">
      <div class="section-head">
        <h3 class="section-title">今日推荐</h3>
        <span class="more" @click="goAiPlan">AI 生成计划 ></span>
      </div>

      <div class="recommend-list">
        <div class="recommend-item card" @click="goAiPlan">
          <div class="rec-icon rec-train">
            <i class="fas fa-dumbbell"></i>
          </div>
          <div class="rec-info">
            <p class="rec-title">胸肌训练计划</p>
            <p class="rec-sub">4组 × 12次 · 系统推荐</p>
          </div>
          <i class="fas fa-chevron-right rec-arrow"></i>
        </div>
        <div class="recommend-item card" @click="goAiPlan">
          <div class="rec-icon rec-diet">
            <i class="fas fa-utensils"></i>
          </div>
          <div class="rec-info">
            <p class="rec-title">减脂饮食搭配</p>
            <p class="rec-sub">每日 1800 kcal · 高蛋白</p>
          </div>
          <i class="fas fa-chevron-right rec-arrow"></i>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onActivated, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { userAPI, type MemberCard } from '@/api/userAPI'
import { type CardType } from '@/api/adminAPI'
import { hasActivityPrice, payPrice, discountLabel } from '@/utils/activityPrice'
import cardBgUrl from '@/static/images/山达健身会员卡背景5.webp'
import cardBgUrl6 from '@/static/images/山达健身会员卡背景6.webp'

// keep-alive 缓存按组件 name 匹配
defineOptions({ name: 'UserHome' })

const router = useRouter()
const authStore = useAuthStore()

// 首屏图片加载状态：未加载显示骨架屏，加载完淡入，避免空白与跳动
const heroLoaded = ref(false)
const thumbLoaded = ref(false)

/** 用 new Image() 探测 CSS 背景图加载完成（已缓存/出错都回调，保证骨架屏必终止） */
const detectBgLoad = (url: string, onLoad: () => void) => {
  const img = new Image()
  let done = false
  const finish = () => {
    if (done) return
    done = true
    onLoad()
  }
  img.onload = finish
  img.onerror = finish
  img.src = url
  if (img.complete) finish()
}

// 会员卡数据：仅用于判断首页引导态（有卡 / 无卡），首页不展示卡内真实数据、不做激活
// 100% 来自 GET /api/v1/user/card
const card = ref<MemberCard | null>(null)

// 首页展示的可购买卡类型（GET /api/v1/user/card-types）
const cardTypes = ref<CardType[]>([])
// 普通会员卡前 2 张
const homeNormalCards = computed(() =>
  cardTypes.value.filter(c => c.category !== 'PT').slice(0, 2)
)
// 私教课卡前 2 张
const homePtCards = computed(() =>
  cardTypes.value.filter(c => c.category === 'PT').slice(0, 2)
)
/** 适用范围展示（与 BuyCards.vue 一致） */
const scopeText = (c: CardType): string =>
  c.scope === 'SINGLE_STORE' ? (c.storeName || '指定门店') : '全店通用'

// 首页快捷办卡：跳转到办理页并直达结算弹层，走"选券 → 折价 → 支付"完整链路
// （原实现直接下单支付，不经过选券，导致"有券也用不上"）
const purchasingId = ref<number | null>(null)
const handleBuy = (c: CardType) => {
  router.push({ path: '/user/buy-cards', query: { cardTypeId: String(c.id) } })
}

// 登录态判定：本地存在 token 即视为已登录（与路由守卫保持一致）。
// 注意：整页加载时（如支付宝支付回跳 /user/pay-result、或直接刷新首页），
// authStore.isLoggedIn 要等 App.vue 里异步的 /auth/user-info 返回后才置为 true，
// 首屏 onMounted 阶段它仍是 false。若用 isLoggedIn 做加载前置条件，
// 会员卡数据会被整体跳过且无人重试 —— 表现为"购买会员卡后首页会员卡区不显示"。
const loggedIn = computed(() => !!authStore.token)

// 后端可能返回 null 或空对象 {}，需检查关键字段 cardNo 是否存在
const hasMemberCard = computed(() => {
  return card.value !== null && card.value !== undefined &&
         !!(card.value.cardNo || card.value.id)
})

// Hero 按钮文案：未登录 / 已登录无卡 / 已登录有卡
const heroBtnText = computed(() => {
  if (!loggedIn.value) return '去登录'
  return hasMemberCard.value ? '查看我的会员卡' : '立即办卡'
})

// Hero 简短引导文案
const heroGuide = computed(() => {
  if (!loggedIn.value) return '登录后即可办卡、核销'
  if (hasMemberCard.value) return '会员卡详情与激活，请前往「我的会员卡」'
  return '立即办卡，开启燃动之旅'
})

// 首页只做跳转，不处理业务：未登录去登录，已登录统一进入会员卡页（办卡 / 查看 / 激活均在该页完成）
const onHeroBtnClick = () => {
  router.push(loggedIn.value ? '/user/membership' : '/login')
}

// 顶部用户条：头像与昵称来自全局登录信息（编辑资料后即时同步）
const homeAvatar = computed(() => resolveAvatar(authStore.userInfo?.avatar))
const homeName = computed(() => {
  if (!loggedIn.value) return '点击登录'
  const nickname = authStore.userInfo?.nickname
  if (nickname) return nickname
  const p = authStore.userInfo?.phone || ''
  return p.length === 11 ? `${p.slice(0, 3)}****${p.slice(7)}` : '山达会员'
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const loadCard = async () => {
  // 无 token 视为未登录：清空数据，Hero 回到"去登录"
  if (!authStore.token) {
    card.value = null
    return
  }
  try {
    const res = await userAPI.getCard()
    if (res.success) {
      // 后端返回 null 或空对象都视为无卡
      card.value = res.data && res.data.cardNo ? res.data : null
    } else {
      card.value = null
    }
  } catch {
    card.value = null
  }
}

// 加载可购买卡类型（首页只展示前 2 张）
const loadCardTypes = async () => {
  if (!authStore.token) {
    cardTypes.value = []
    return
  }
  try {
    const res = await userAPI.getCardTypes()
    if (res.success) cardTypes.value = res.data?.list || []
  } catch {
    /* 错误由拦截器统一提示 */
  }
}

/** 统一刷新：会员卡（Hero 引导态）+ 可购卡类型（热门会员卡区） */
const refreshMemberCard = () => {
  loadCard()
  loadCardTypes()
}

onMounted(() => {
  refreshMemberCard()
  // 探测首屏背景图加载完成（CSS background-image 无 onload 事件，用 new Image() 探测），
  // 加载完触发骨架屏淡出、背景图淡入。已缓存时同步完成，骨架屏不闪。
  detectBgLoad(cardBgUrl, () => (heroLoaded.value = true))
  detectBgLoad(cardBgUrl6, () => (thumbLoaded.value = true))
})

// keep-alive 缓存页：除首次挂载外，每次重新激活（购卡成功返回首页 / 切换 tab / 从会员卡页返回）
// 都重新拉取一次，保证新购 / 新激活的会员卡状态立即反映到首页
let activatedOnce = false
onActivated(() => {
  if (!activatedOnce) {
    activatedOnce = true
    return
  }
  refreshMemberCard()
})

// 登录态变化（登录 / 退出登录）时同步刷新，避免沿用上一个账号的会员卡数据
watch(() => authStore.token, () => refreshMemberCard())

const goAiPlan = () => router.push('/user/ai-plan')
const goCard = () => router.push('/user/membership')
const goBuyCards = () => router.push('/user/buy-cards')
const goRecords = () => router.push('/user/my-records')
const goMyShare = () => router.push('/user/share')
const goProfile = () => router.push('/user/profile')
</script>

<style scoped>
.home-page {
  padding: var(--spacing-md);
}

/* ==================== 顶部用户条 ==================== */
.home-user-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 4px 2px var(--spacing-sm);
}

.hub-avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.hub-text {
  flex: 1;
  min-width: 0;
}

.hub-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hub-arrow {
  font-size: 11px;
  color: var(--color-text-placeholder);
}

/* ==================== 品牌会员卡 Hero ==================== */
.hero-card {
  position: relative;
  border-radius: 20px;
  overflow: hidden;
  min-height: 200px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
  margin-bottom: var(--spacing-xs);
}

.hero-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.hero-bg.is-loaded {
  opacity: 1;
}

/* 骨架屏占位：图片未加载完时显示，shimmer 动画过渡，加载完 v-if 移除 */
.hero-skeleton {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(
    100deg,
    rgba(40, 40, 55, 0.9) 30%,
    rgba(60, 60, 80, 0.95) 50%,
    rgba(40, 40, 55, 0.9) 70%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.hero-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(
    135deg,
    rgba(0, 0, 0, 0.35) 0%,
    rgba(0, 0, 0, 0.15) 40%,
    rgba(0, 0, 0, 0.55) 100%
  );
}

.hero-content {
  position: relative;
  z-index: 3;
  padding: 20px 18px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-content.center {
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 200px;
}

.brand-name {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 3px;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.3);
}

/* 品牌引导文案与按钮（三态统一居中展示，不展示真实卡数据） */
.hero-main-text {
  font-size: 18px;
  font-weight: 700;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.3);
  margin-top: 8px;
}

.hero-btn {
  margin-top: 12px;
  padding: 10px 28px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
}

/* 小欢迎语 */
.greeting-line {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-md);
  padding: 4px 0;
}

/* ==================== 快捷入口 ==================== */
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-sm);
}

.quick-item {
  background: #fff;
  border-radius: var(--radius-md);
  padding: var(--spacing-md) 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: var(--shadow-card);
}

.quick-item i {
  font-size: 22px;
  color: var(--color-primary);
  margin-bottom: var(--spacing-sm);
}

.quick-item span {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* ==================== 热门会员卡（普通卡 + 私教课卡各前 2 张） ==================== */
.cards-section {
  margin-top: var(--spacing-md);
}

.hc-group {
  margin-top: var(--spacing-sm);
}

.hc-group:first-of-type {
  margin-top: 0;
}

.hc-group-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 var(--spacing-sm) 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.hc-group-title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: var(--gradient-primary);
}

.hc-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

/* 三段横排卡片：[左品牌小卡] [中卡信息] [右办理按钮] */
.hc-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background-color: #fff;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  min-height: 100px;
}

/* 左侧品牌小卡 88×64 */
.hc-thumb {
  position: relative;
  flex: 0 0 88px;
  width: 88px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 3px 12px rgba(255, 107, 53, 0.25);
  /* 缩略图背景图加载前的占位底色，避免空白 */
  background-color: #2a2a3e;
}

.pt-thumb {
  box-shadow: 0 3px 12px rgba(255, 158, 0, 0.3);
}

.hc-thumb-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.hc-thumb-bg.is-loaded {
  opacity: 1;
}

.hc-thumb-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(135deg, rgba(0,0,0,0.2) 0%, rgba(0,0,0,0.1) 40%, rgba(0,0,0,0.45) 100%);
}

.hc-thumb-content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.hc-thumb-icon {
  font-size: 18px;
  color: var(--color-primary);
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  margin-bottom: 2px;
}

.pt-thumb .hc-thumb-icon {
  color: #ffb02e;
}

.hc-thumb-name {
  font-size: 11px;
  font-weight: 700;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 72px;
}

/* 中间卡信息 */
.hc-mid {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.hc-type {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hc-days {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0;
}

.hc-scope {
  font-size: 11px;
  color: var(--color-text-placeholder);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.hc-price {
  display: flex;
  align-items: baseline;
  color: var(--color-primary);
  margin: 0;
}

.hc-currency {
  font-size: 12px;
  font-weight: 600;
}

.hc-amount {
  font-size: 20px;
  font-weight: 800;
  margin-left: 2px;
  line-height: 1;
}

/* 原价划线（有活动价时） */
.hc-origin-price {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-placeholder);
  text-decoration: line-through;
}

/* 限时活动标签 */
.hc-act-row {
  margin: 2px 0 0;
}

.hc-act-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #ff6b35, #ff4d4f);
  line-height: 1.5;
}

/* 右侧办理按钮 */
.hc-right {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
}

.hc-btn {
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

.hc-btn:active {
  transform: scale(0.96);
}

.hc-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ==================== 今日推荐 ==================== */
.hot-section {
  margin-top: var(--spacing-md);
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-dark);
}

.more {
  font-size: 13px;
  color: var(--color-primary);
  cursor: pointer;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.recommend-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md);
  gap: var(--spacing-md);
  cursor: pointer;
}

.rec-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
}

.rec-train {
  background: linear-gradient(135deg, #ff6b1a, #ff8c42);
}

.rec-diet {
  background: linear-gradient(135deg, #52c41a, #7cc576);
}

.rec-info {
  flex: 1;
}

.rec-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 4px;
}

.rec-sub {
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.rec-arrow {
  color: var(--color-text-placeholder);
  font-size: 12px;
}
</style>
