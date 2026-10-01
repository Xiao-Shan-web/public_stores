<template>
  <div class="activity-user page-container">
    <PageHeader title="限时活动" />

    <div class="page-body">
      <p v-if="loading" class="loading-hint">加载中…</p>

      <div v-else-if="!list.length" class="empty-state">
        <i class="fas fa-bullhorn"></i>
        <p>暂无进行中的活动</p>
      </div>

      <div v-else class="act-list">
        <div class="act-item" v-for="a in list" :key="a.id">
          <div class="act-banner">
            <i class="fas fa-fire act-fire"></i>
            <span class="act-tag">限时</span>
            <span class="act-countdown">{{ countdown(a) }}</span>
          </div>
          <div class="act-content">
            <h3 class="act-title">{{ a.title }}</h3>
            <p class="act-sub" v-if="a.subtitle">{{ a.subtitle }}</p>

            <div class="act-card-info">
              <span class="card-name">{{ a.cardTypeName || '会员卡' }}</span>
              <div class="price-wrap">
                <span class="price-old">¥{{ a.originalPrice }}</span>
                <span class="price-new">¥{{ a.activityPrice }}</span>
                <span class="discount-badge">{{ discountText(a.discount) }}</span>
              </div>
            </div>

            <div class="act-meta">
              <span><i class="far fa-clock"></i>{{ fmtTime(a.startTime) }} ~ {{ fmtTime(a.endTime) }}</span>
              <span v-if="a.quotaTotal != null">
                <i class="fas fa-users"></i>剩余 {{ Math.max(a.quotaTotal - a.quotaUsed, 0) }} 个名额
              </span>
            </div>

            <button class="act-btn" :disabled="purchasingId !== null" @click="buy(a)">
              {{ purchasingId === a.cardTypeId ? '支付中…' : '立即抢购' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { message } from '@/utils/message'
import { getOngoingActivities } from '@/api/operationAPI'
import type { ActivityItem } from '@/api/operationAPI'
import { userAPI } from '@/api/userAPI'
import { useRouter } from 'vue-router'

const router = useRouter()
const list = ref<ActivityItem[]>([])
const loading = ref(false)
const purchasingId = ref<number | null>(null)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getOngoingActivities()
    if (res.success && res.data?.list) list.value = res.data.list
  } catch { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

/** 活动抢购：直接建单（服务端按活动价计算），随后进入支付 */
const buy = async (a: ActivityItem) => {
  if (purchasingId.value !== null) return
  purchasingId.value = a.cardTypeId
  try {
    const createRes = await userAPI.createOrder(a.cardTypeId)
    if (!createRes.success || !createRes.data?.orderNo) return
    const payRes = await userAPI.payOrder(createRes.data.orderNo)
    if (!payRes.success) return

    const data = payRes.data
    if (data?.payFormHtml) {
      const w = window.open('', '_blank')
      if (w) {
        w.document.open()
        w.document.write(data.payFormHtml)
        w.document.close()
      }
      message.success('即将跳转支付宝完成支付')
    } else {
      message.success('支付成功，会员卡已生成')
      router.replace('/user/membership')
    }
  } catch { /* 拦截器已提示 */ } finally {
    purchasingId.value = null
  }
}

/** 倒计时文案（活动结束时间为准） */
const countdown = (a: ActivityItem) => {
  const end = new Date(a.endTime.replace('T', ' ').replace(/-/g, '/')).getTime()
  const diff = end - Date.now()
  if (diff <= 0) return '已结束'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  if (days > 0) return `剩 ${days} 天 ${hours} 小时`
  const mins = Math.floor((diff % 3600000) / 60000)
  return `剩 ${hours} 小时 ${mins} 分`
}

const discountText = (d: string | number) => {
  const n = Number(d)
  if (!n) return ''
  const tenths = n * 10
  return Math.abs(tenths - Math.round(tenths)) < 0.01
    ? `${Math.round(tenths)} 折`
    : `${tenths.toFixed(1)} 折`
}

const fmtTime = (t: string) => (t ? t.replace('T', ' ').substring(5, 16) : '-')

onMounted(() => { loadList() })
</script>

<style scoped>
.activity-user { min-height: 100vh; background: var(--color-bg); }

.loading-hint {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 40px 0;
  font-size: 14px;
}

.act-list { display: flex; flex-direction: column; gap: 14px; }

.act-item {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.06);
}

.act-banner {
  height: 64px;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  color: #fff;
}

.act-fire { font-size: 18px; }

.act-tag {
  font-size: 12px;
  background: rgba(255, 255, 255, 0.25);
  padding: 2px 8px;
  border-radius: 10px;
}

.act-countdown {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  background: rgba(0, 0, 0, 0.18);
  padding: 3px 10px;
  border-radius: 10px;
}

.act-content { padding: 14px; }

.act-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.act-sub {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 4px;
}

.act-card-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 10px;
  padding: 10px 12px;
  background: var(--color-bg, #fafafa);
  border-radius: 8px;
  gap: 10px;
}

.card-name {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-wrap {
  display: flex;
  align-items: baseline;
  gap: 6px;
  flex-shrink: 0;
}

.price-old {
  font-size: 12px;
  color: var(--color-text-placeholder);
  text-decoration: line-through;
}

.price-new {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-danger, #ff4d4f);
}

.discount-badge {
  font-size: 11px;
  color: #fff;
  background: var(--color-danger, #ff4d4f);
  padding: 1px 6px;
  border-radius: 8px;
}

.act-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-bottom: 12px;
}

.act-meta i { margin-right: 5px; }

.act-btn {
  width: 100%;
  border: none;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  padding: 11px 0;
  border-radius: 22px;
  cursor: pointer;
}

.act-btn:disabled { opacity: 0.65; cursor: not-allowed; }
</style>
