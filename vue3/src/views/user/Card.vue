<template>
  <div class="card-page page-container">
    <PageHeader title="我的会员卡" />
    <div class="page-body">
      <!-- 有会员卡：卡片列表 -->
      <div v-if="cards.length" class="card-list">
        <div v-for="card in cards" :key="card.id" class="mc-card">
          <!-- 背景图层（固定单张，品牌文字由背景图承担） -->
          <div class="mc-bg" :style="{ backgroundImage: `url(${bgUrl6})` }"></div>
          <!-- 遮罩层 -->
          <div class="mc-overlay" :class="overlayClass(card.status)"></div>

          <!-- 卡片内容（左上角留空，避开背景图品牌文字） -->
          <div class="mc-content">
            <!-- 1. 会员卡类型 -->
            <p class="mc-card-type">{{ card.cardName }}</p>
            <!-- 1.1 分类 + 适用范围 -->
            <div class="mc-meta-row">
              <span class="mc-cat-tag" :class="isPtCard(card) ? 'mc-cat-pt' : 'mc-cat-normal'">
                {{ isPtCard(card) ? '私教课卡' : '普通卡' }}
              </span>
              <span class="mc-scope">
                <i class="fas fa-store"></i>{{ scopeText(card) }}
              </span>
            </div>
            <!-- 2. 会员卡号 -->
            <p class="mc-card-no">{{ maskCardNo(card.cardNo) }}</p>

            <!-- 3. 核心数据区 -->
            <div class="mc-core">
              <template v-if="isTimesCard(card)">
                <div class="mc-stat">
                  <p class="mc-num">{{ card.remainTimes }}</p>
                  <p class="mc-label">剩余节数</p>
                </div>
                <div class="mc-stat-divider"></div>
                <div class="mc-stat">
                  <p class="mc-num">{{ card.totalTimes }}</p>
                  <p class="mc-label">总节数</p>
                </div>
              </template>
              <template v-else>
                <div class="mc-stat">
                  <p class="mc-num">{{ card.remainingDays ?? '-' }}</p>
                  <p class="mc-label">剩余天数</p>
                </div>
              </template>
            </div>

            <!-- 4. 状态（私教课卡不可刷脸入场） -->
            <div class="mc-status-row">
              <span class="mc-status" :class="statusClass(card.status)">{{ statusText(card.status) }}</span>
              <span class="mc-entry" v-if="card.status === 1 && !isPtCard(card)">
                <i class="fas fa-check-circle"></i> 可入场
              </span>
              <span class="mc-entry mc-entry-pt" v-else-if="card.status === 1 && isPtCard(card)">
                <i class="fas fa-dumbbell"></i> 私教课程使用
              </span>
            </div>

            <!-- 5. 底部信息 -->
            <div class="mc-footer">
              <div class="mc-footer-item">
                <span class="mc-footer-label">办理时间</span>
                <span class="mc-footer-value">{{ formatDate(card.createTime) }}</span>
              </div>
              <div class="mc-footer-item">
                <span class="mc-footer-label">激活时间</span>
                <span class="mc-footer-value" :class="{ 'mc-val-empty': !card.startTime }">
                  {{ card.startTime ? formatDate(card.startTime) : '未激活' }}
                </span>
              </div>
              <div class="mc-footer-item">
                <span class="mc-footer-label">生效时间</span>
                <span class="mc-footer-value">{{ formatDate(card.startTime) }}</span>
              </div>
              <div class="mc-footer-item" v-if="card.expireTime">
                <span class="mc-footer-label">到期时间</span>
                <span class="mc-footer-value">{{ formatDate(card.expireTime) }}</span>
              </div>
            </div>
          </div>

          <!-- 未激活：立即激活（每张卡独立） -->
          <div class="mc-action-bar" v-if="card.status === 0">
            <button class="mc-btn-activate" :disabled="activatingId === card.id" @click="handleActivate(card.id)">
              <i :class="activatingId === card.id ? 'fas fa-spinner fa-spin' : 'fas fa-bolt'"></i>
              {{ activatingId === card.id ? '激活中...' : '立即激活' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 无卡 -->
      <div v-else class="mc-empty">
        <i class="fas fa-id-card"></i>
        <p>暂无会员卡</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userAPI, type MemberCard } from '@/api/userAPI'
import message from '@/utils/message'
import PageHeader from '@/components/PageHeader.vue'
import bgUrl6 from '@/static/images/山达健身会员卡背景6.webp'

defineOptions({ name: 'UserMembership' })

const cards = ref<MemberCard[]>([])
const loading = ref(false)
const activatingId = ref<number | null>(null)

/** 按节数使用的卡（PT 私教课卡） */
const isTimesCard = (card: MemberCard) => Number(card.totalTimes) > 0
/** 是否私教课卡（历史卡缺省 category 时按普通卡处理） */
const isPtCard = (card: MemberCard) => card.category === 'PT'
/** 适用范围文案 */
const scopeText = (card: MemberCard): string =>
  card.scope === 'SINGLE_STORE' ? (card.storeName || '指定门店') : '全店通用'

const statusText = (s: number): string => {
  const map = ['未激活', '生效中', '已过期', '已停用']
  return map[s] || '未知'
}

const statusClass = (s: number): string => {
  return ['mc-st-unactivated', 'mc-st-active', 'mc-st-expired', 'mc-st-disabled'][s] || ''
}

const overlayClass = (s: number): string => {
  if (s === 2 || s === 3) return 'mc-overlay-gray'
  if (s === 0) return 'mc-overlay-orange'
  return ''
}

// 卡号打码
const maskCardNo = (no: string): string => {
  if (!no) return '-'
  if (no.length <= 8) return no
  return no.substring(0, 4) + '****' + no.substring(no.length - 4)
}

const formatDate = (t: string): string => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 10)
}

// 激活指定会员卡
const handleActivate = async (id: number) => {
  if (activatingId.value === id) return
  activatingId.value = id
  try {
    const res = await userAPI.activateMembership(id)
    if (res.success) {
      message.success('会员卡已激活')
      await loadCards()
    } else {
      message.error(res.message || '激活失败')
    }
  } catch {
    /* request 拦截器已 toast */
  } finally {
    activatingId.value = null
  }
}

const loadCards = async () => {
  loading.value = true
  try {
    const res = await userAPI.getMemberships()
    if (res.success) cards.value = res.data || []
  } catch {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

onMounted(loadCards)
</script>

<style scoped>
.card-page {
  display: flex;
  flex-direction: column;
}

.page-body {
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ==================== 卡片列表 ==================== */
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ==================== 单张会员卡 ==================== */
.mc-card {
  position: relative;
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  border-radius: 20px;
  overflow: hidden;
  min-height: 240px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.2);
}

.mc-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  z-index: 0;
}

.mc-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(
    135deg,
    rgba(0, 0, 0, 0.25) 0%,
    rgba(0, 0, 0, 0.1) 40%,
    rgba(0, 0, 0, 0.5) 100%
  );
}

.mc-overlay-orange {
  background: linear-gradient(
    135deg,
    rgba(255, 158, 0, 0.15) 0%,
    rgba(0, 0, 0, 0.2) 50%,
    rgba(255, 158, 0, 0.1) 100%
  );
}

.mc-overlay-gray {
  background: linear-gradient(
    135deg,
    rgba(60, 60, 60, 0.55) 0%,
    rgba(40, 40, 40, 0.5) 40%,
    rgba(30, 30, 30, 0.7) 100%
  );
}

.mc-content {
  position: relative;
  z-index: 3;
  padding: 40px 18px 18px;
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mc-card-type {
  font-size: 20px;
  font-weight: 800;
  margin-top: 4px;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.35);
}

/* 分类标签 + 适用范围 */
.mc-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.mc-cat-tag {
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 10px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.mc-cat-normal {
  background: rgba(24, 144, 255, 0.35);
  color: #cfe7ff;
  border: 1px solid rgba(24, 144, 255, 0.5);
}

.mc-cat-pt {
  background: rgba(255, 158, 0, 0.35);
  color: #ffdd99;
  border: 1px solid rgba(255, 158, 0, 0.55);
}

.mc-scope {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  opacity: 0.85;
}

.mc-card-no {
  font-size: 13px;
  font-family: 'Courier New', 'Consolas', monospace;
  letter-spacing: 2px;
  opacity: 0.85;
  margin-top: 2px;
}

.mc-core {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  backdrop-filter: blur(6px);
}

.mc-stat {
  flex: 1;
  text-align: center;
}

.mc-num {
  font-size: 24px;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.3);
}

.mc-label {
  font-size: 11px;
  opacity: 0.8;
  margin-top: 4px;
}

.mc-stat-divider {
  width: 1px;
  height: 36px;
  background: rgba(255, 255, 255, 0.25);
}

.mc-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.mc-status {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.mc-st-active {
  background: rgba(82, 196, 26, 0.35);
  color: #b7eb6f;
  border: 1px solid rgba(82, 196, 26, 0.5);
}

.mc-st-unactivated {
  background: rgba(255, 158, 0, 0.35);
  color: #ffd666;
  border: 1px solid rgba(255, 158, 0, 0.5);
}

.mc-st-expired,
.mc-st-disabled {
  background: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.mc-entry {
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 10px;
  background: rgba(82, 196, 26, 0.3);
  color: #b7eb6f;
}

.mc-entry-pt {
  background: rgba(255, 158, 0, 0.3);
  color: #ffdd99;
}

.mc-entry i {
  margin-right: 2px;
}

.mc-footer {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.15);
}

.mc-footer-item {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.mc-footer-label {
  opacity: 0.7;
}

.mc-footer-value {
  opacity: 0.9;
  font-weight: 500;
}

.mc-val-empty {
  color: #ffb84d;
  font-style: italic;
}

/* ==================== 激活按钮 ==================== */
.mc-action-bar {
  position: relative;
  z-index: 3;
  padding: 0 18px 14px;
  display: flex;
  justify-content: center;
}

.mc-btn-activate {
  padding: 10px 24px;
  background: linear-gradient(135deg, #ff9e00, #ffb340);
  color: #fff;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  box-shadow: 0 4px 14px rgba(255, 158, 0, 0.35);
}

.mc-btn-activate:disabled {
  opacity: 0.6;
}

/* ==================== 空状态 ==================== */
.mc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 40px var(--spacing-md);
  color: var(--color-text-secondary);
}

.mc-empty > i {
  font-size: 48px;
  color: var(--color-text-placeholder);
}
</style>
