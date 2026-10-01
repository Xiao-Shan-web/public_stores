<template>
  <div class="pay-result-page page-container">
    <PageHeader title="支付结果" />
    <div class="page-body">
      <!-- 查询中 -->
      <div v-if="checking" class="pr-state">
        <div class="pr-spinner">
          <i class="fas fa-spinner fa-spin"></i>
        </div>
        <p class="pr-state-text">正在确认支付结果…</p>
        <p class="pr-state-sub" v-if="pollCount > 1">已尝试 {{ pollCount }} 次，请稍候</p>
      </div>

      <!-- 支付成功 -->
      <div v-else-if="paid" class="pr-state">
        <div class="pr-icon pr-icon-success">
          <i class="fas fa-check"></i>
        </div>
        <p class="pr-state-text">支付成功，会员卡已生成</p>
        <p class="pr-state-sub">请尽快激活会员卡并录入人脸</p>
        <button class="pr-btn pr-btn-primary" @click="goMembership">
          <i class="fas fa-id-card"></i> 查看我的会员卡
        </button>
      </div>

      <!-- 未确认到支付 -->
      <div v-else class="pr-state">
        <div class="pr-icon pr-icon-pending">
          <i class="fas fa-clock"></i>
        </div>
        <p class="pr-state-text">暂未确认到支付结果</p>
        <p class="pr-state-sub">若您已完成支付，请稍后重新查看</p>
        <div class="pr-btn-row">
          <button class="pr-btn pr-btn-primary" :disabled="repaying" @click="repay">
            <i class="fas fa-redo" :class="{ 'fa-spin': repaying }"></i>
            {{ repaying ? '跳转中…' : '重新支付' }}
          </button>
          <button class="pr-btn pr-btn-plain" @click="goHome">返回首页</button>
        </div>
        <button class="pr-btn-link" @click="refresh">
          <i class="fas fa-sync-alt"></i> 我已支付，刷新结果
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userAPI } from '@/api/userAPI'
import { message } from '@/utils/message'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const router = useRouter()

const checking = ref(true)
const paid = ref(false)
const repaying = ref(false)
const pollCount = ref(0)
const orderNo = ref('')

let pollTimer: ReturnType<typeof setTimeout> | null = null
const MAX_POLL = 6          // 最多轮询 6 次
const POLL_INTERVAL = 2000  // 每次间隔 2s

/** 查询订单状态 */
const queryOrder = async (): Promise<boolean> => {
  if (!orderNo.value) return false
  try {
    const res = await userAPI.getOrder(orderNo.value)
    if (res.success && res.data) {
      return res.data.status === 'PAID'
    }
  } catch (e) {
    /* 静默，继续轮询 */
  }
  return false
}

/** 轮询：支付宝同步跳回时，异步 notify 可能尚未到达 */
const poll = async () => {
  pollCount.value++
  const isPaid = await queryOrder()
  if (isPaid) {
    paid.value = true
    checking.value = false
    return
  }
  if (pollCount.value >= MAX_POLL) {
    checking.value = false
    return
  }
  pollTimer = setTimeout(poll, POLL_INTERVAL)
}

/** 手动刷新 */
const refresh = () => {
  checking.value = true
  paid.value = false
  pollCount.value = 0
  poll()
}

/** 重新支付：对同一笔 PENDING 订单再次发起支付 */
const repay = async () => {
  if (repaying.value || !orderNo.value) return
  repaying.value = true
  try {
    const res = await userAPI.payOrder(orderNo.value)
    if (!res.success) return
    const data = res.data
    if (data?.payUrl) {
      // 优先：顶层导航直达支付宝，规避新窗口/隐藏容器提交被拦截导致空白页
      window.location.href = data.payUrl
    } else if (data?.payFormHtml) {
      // 兜底：隐藏容器提交表单
      submitAlipayForm(data.payFormHtml)
    } else if (data?.status === 'PAID') {
      // mock 模式或已支付
      paid.value = true
      message.success('支付成功，会员卡已生成')
    }
  } catch (e) {
    /* 错误由拦截器统一提示 */
  } finally {
    repaying.value = false
  }
}

/** 兜底路径：当前页注入隐藏容器并 submit 表单（仅在未返回 payUrl 时使用） */
const submitAlipayForm = (html: string) => {
  const container = document.createElement('div')
  container.style.display = 'none'
  container.innerHTML = html
  document.body.appendChild(container)
  const form = container.querySelector('form')
  if (form) {
    form.target = '_self'
    form.submit()
  }
}

const goMembership = () => router.replace('/user/membership')
const goHome = () => router.replace('/user/home')

onMounted(() => {
  // 支付宝同步回跳携带 out_trade_no；兼容前端自行传 orderNo
  orderNo.value = (route.query.orderNo as string) || (route.query.out_trade_no as string) || ''
  if (!orderNo.value) {
    checking.value = false
    return
  }
  poll()
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<style scoped>
.pay-result-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--color-bg);
}

.page-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-lg) var(--spacing-md);
}

.pr-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  width: 100%;
  max-width: 320px;
}

/* ==================== 图标 ==================== */
.pr-icon {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  margin-bottom: var(--spacing-md);
}

.pr-icon-success {
  background: rgba(82, 196, 26, 0.12);
  color: #52c41a;
  box-shadow: 0 0 0 6px rgba(82, 196, 26, 0.08);
  animation: pr-pop 0.4s ease;
}

.pr-icon-pending {
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-text-secondary);
}

.pr-spinner {
  font-size: 40px;
  color: var(--color-primary);
  margin-bottom: var(--spacing-md);
}

@keyframes pr-pop {
  0% { transform: scale(0.6); opacity: 0; }
  70% { transform: scale(1.08); }
  100% { transform: scale(1); opacity: 1; }
}

/* ==================== 文案 ==================== */
.pr-state-text {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 6px;
}

.pr-state-sub {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 var(--spacing-lg);
  line-height: 1.6;
}

/* ==================== 按钮 ==================== */
.pr-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 11px 28px;
  border-radius: 22px;
  font-size: 14px;
  font-weight: 600;
  transition: transform 0.15s, opacity 0.15s;
}

.pr-btn:active {
  transform: scale(0.96);
}

.pr-btn-primary {
  background: var(--gradient-primary);
  color: #fff;
  box-shadow: 0 4px 14px rgba(255, 107, 53, 0.3);
}

.pr-btn-plain {
  background: #fff;
  color: var(--color-text);
  border: 1px solid var(--color-border);
}

.pr-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.pr-btn-row {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.pr-btn-link {
  background: none;
  font-size: 13px;
  color: var(--color-primary);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
}
</style>
