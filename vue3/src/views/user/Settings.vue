<template>
  <div class="settings-page page-container">
    <PageHeader title="设置" />
    <div class="page-body">
    <div class="info-card card">
      <h3 class="section-title">个人信息</h3>
      <div class="info-row">
        <span class="label">手机号</span>
        <span class="value">{{ maskedPhone }}</span>
      </div>
      <div class="info-row">
        <span class="label">用户ID</span>
        <span class="value">{{ authStore.userInfo?.id || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="label">账号状态</span>
        <span class="value" :class="statusClass">
          {{ authStore.userInfo?.status ? '正常' : '禁用' }}
        </span>
      </div>
    </div>

    <div class="info-card card">
      <h3 class="section-title">关于</h3>
      <div class="info-row">
        <span class="label">应用版本</span>
        <span class="value">v1.0.0</span>
      </div>
      <div class="info-row" style="cursor:pointer" @click="showAgreement = true">
        <span class="label">用户协议</span>
        <i class="fas fa-chevron-right arrow"></i>
      </div>
    </div>

    <button class="btn-logout" @click="handleLogout">
      <i class="fas fa-sign-out-alt"></i>
      <span>退出登录</span>
    </button>
    </div>

    <!-- 用户协议弹窗 -->
    <div v-if="showAgreement" class="modal-mask" @click.self="showAgreement = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3>用户协议</h3>
          <i class="fas fa-times modal-close" @click="showAgreement = false"></i>
        </div>
        <div class="modal-body">
          <p>本应用提供健身会员卡管理、入场核销、会员分享等服务。使用本应用即视为同意以下条款：</p>
          <h4 class="ag-title">1. 服务说明</h4>
          <p class="ag-text">提供会员卡购买、激活、入场核销、会员分享等服务。</p>
          <h4 class="ag-title">2. 会员卡使用规则</h4>
          <ul class="ag-list">
            <li>会员卡仅限本人使用</li>
            <li>入场时需进行人脸识别验证</li>
            <li>会员卡过期后需续费才能继续使用</li>
            <li>仅 ACTIVE 会员卡用户可发布分享</li>
          </ul>
          <h4 class="ag-title">3. 隐私保护</h4>
          <ul class="ag-list">
            <li>手机号仅用于账号登录</li>
            <li>人脸信息仅用于入场核销</li>
            <li>数据加密存储，不向第三方泄露</li>
          </ul>
        </div>
        <div class="modal-footer">
          <button class="btn-confirm" @click="showAgreement = false">我知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authAPI } from '@/api/authAPI'
import message from '@/utils/message'
import { confirm } from '@/composables/useConfirm'
import PageHeader from '@/components/PageHeader.vue'

const router = useRouter()
const authStore = useAuthStore()
const showAgreement = ref(false)

const maskedPhone = computed(() => {
  const p = authStore.userInfo?.phone || ''
  if (p.length === 11) return `${p.slice(0, 3)}****${p.slice(7)}`
  return p || '-'
})

const statusClass = computed(() => (authStore.userInfo?.status ? 'st-active' : 'st-disabled'))

const handleLogout = async () => {
  const ok = await confirm({
    title: '退出确认',
    content: '确定要退出登录吗？',
    confirmText: '退出登录',
    cancelText: '取消',
    danger: true
  })
  if (!ok) return
  try {
    await authAPI.logout()
  } catch {
    /* 忽略后端错误，前端照常登出 */
  }
  authStore.logout()
  message.success('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
}

.page-body {
  padding: var(--spacing-md);
}

.info-card {
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--color-border);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.info-row:last-child {
  border-bottom: none;
}

.label {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.value {
  font-size: 14px;
  color: var(--color-text);
  font-weight: 500;
}

.st-active {
  color: #52c41a;
}

.st-disabled {
  color: var(--color-danger, #ff4d4f);
}

.arrow {
  color: var(--color-text-placeholder);
  font-size: 12px;
  cursor: pointer;
}

.btn-logout {
  width: 100%;
  background: #fff;
  color: var(--color-danger, #ff4d4f);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  cursor: pointer;
}

.btn-logout:active {
  background: var(--color-bg-gray);
}

/* 协议弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--spacing-md);
}

.modal-card {
  width: 100%;
  max-width: 400px;
  max-height: 80vh;
  background: #fff;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  font-size: 16px;
  font-weight: 700;
}

.modal-close {
  color: var(--color-text-placeholder);
  font-size: 18px;
  cursor: pointer;
}

.modal-body {
  padding: var(--spacing-md) var(--spacing-lg);
  overflow-y: auto;
  flex: 1;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.ag-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-dark);
  margin-top: var(--spacing-md);
}

.ag-title:first-of-type {
  margin-top: var(--spacing-sm);
}

.ag-list {
  padding-left: var(--spacing-md);
}

.ag-list li {
  list-style: disc;
  margin-top: 4px;
}

.modal-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
}

.btn-confirm {
  width: 100%;
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}
</style>
