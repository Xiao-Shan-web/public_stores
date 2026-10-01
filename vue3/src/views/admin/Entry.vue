<template>
  <div class="entry-page">
    <!-- 核销操作区 -->
    <div class="verify-section card" v-if="!result && !failed && !verifying">
      <div class="section-head">
        <h3 class="section-title">到店刷脸核销</h3>
      </div>
      <CameraCapture facing-mode="user" @capture="onCapture" />
      <button
        v-if="imageBase64"
        class="btn-verify"
        :disabled="verifying"
        @click="verify"
      >
        <i class="fas fa-fingerprint"></i> 开始核销
      </button>
    </div>

    <!-- 核销中 -->
    <div class="verifying card" v-if="verifying">
      <div class="spinner"></div>
      <p class="verifying-text">人脸匹配中...</p>
    </div>

    <!-- 核销失败（醒目提示，不允许静默失败） -->
    <transition name="success-pop" v-if="failed">
      <div class="result-section">
        <div class="fail-anim">
          <div class="fail-circle">
            <i class="fas fa-times"></i>
          </div>
        </div>
        <p class="fail-title">审核失败</p>
        <p class="fail-reason">{{ failed }}</p>

        <div class="member-info card">
          <p class="fail-tips">可能原因：</p>
          <p class="fail-tips-item">· 未识别到人脸，请正对镜头重新拍摄</p>
          <p class="fail-tips-item">· 该会员人脸未录入系统</p>
          <p class="fail-tips-item">· 摄像头权限异常或照片不清晰</p>
        </div>

        <button class="btn-continue" @click="reset">重新核销</button>
      </div>
    </transition>

    <!-- 核销成功动画 -->
    <transition name="success-pop" v-if="result">
      <div class="result-section">
        <div class="success-anim">
          <div class="success-circle">
            <i class="fas fa-check"></i>
          </div>
          <div class="ripple ripple-1"></div>
          <div class="ripple ripple-2"></div>
          <div class="ripple ripple-3"></div>
        </div>
        <p class="success-title">核销成功</p>
        <p class="success-time">{{ formatTime(result.entryTime) }}</p>

        <div class="member-info card">
          <div class="mi-row">
            <span class="mi-label">会员</span>
            <span class="mi-value">{{ result.cardName || '会员卡' }}</span>
          </div>
          <div class="mi-row">
            <span class="mi-label">卡号</span>
            <span class="mi-value">{{ result.cardNo }}</span>
          </div>
          <div class="mi-row" v-if="result.remainingTimes !== null">
            <span class="mi-label">剩余次数</span>
            <span class="mi-value">{{ result.remainingTimes }}</span>
          </div>
          <div class="mi-row" v-if="result.endTime">
            <span class="mi-label">到期时间</span>
            <span class="mi-value">{{ formatDate(result.endTime) }}</span>
          </div>
        </div>

        <button class="btn-continue" @click="reset">继续核销</button>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CameraCapture from '@/components/CameraCapture.vue'
import { entryVerify, type EntryVerifyResult } from '@/api/adminAPI'

defineOptions({ name: 'AdminEntry' })

const imageBase64 = ref<string>('')
const verifying = ref(false)
const result = ref<EntryVerifyResult | null>(null)
/** 审核失败原因（非空表示失败态） */
const failed = ref<string>('')

const onCapture = (base64: string) => {
  imageBase64.value = base64
}

const verify = async () => {
  if (!imageBase64.value || verifying.value) return
  verifying.value = true
  try {
    const res = await entryVerify(imageBase64.value)
    // 唯一成功判定：后端返回 data.result === 'SUCCESS'（与记录列表同源）
    // 后端任何失败（人脸比对失败 / 会员卡无效 / 已过期 / 次数尽）都返回 res.success=false
    if (res.success && res.data && res.data.result === 'SUCCESS') {
      result.value = res.data
    } else {
      // 人脸比对失败 / 会员卡校验失败：明确提示，不允许静默
      failed.value = res.message || '入场失败：请重新拍摄或联系工作人员'
    }
  } catch {
    // HTTP 异常（拦截器已 toast 具体原因），页面同时给出失败态兜底
    failed.value = '系统繁忙或网络异常，请稍后重试'
  } finally {
    verifying.value = false
  }
}

const reset = () => {
  imageBase64.value = ''
  result.value = null
  failed.value = ''
}

const formatTime = (t: string) => {
  return t.replace('T', ' ').substring(0, 19)
}

const formatDate = (t: string) => {
  return t.replace('T', ' ').substring(0, 10)
}
</script>

<style scoped>
.entry-page {
  display: flex;
  flex-direction: column;
}

.verify-section {
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
}

.section-head {
  width: 100%;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-dark);
}

.btn-verify {
  width: 100%;
  padding: 8px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.btn-verify:disabled {
  opacity: 0.6;
}

.verifying {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-md);
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(255, 107, 53, 0.2);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.verifying-text {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.result-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
}

.success-anim {
  position: relative;
  width: 84px;
  height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-circle {
  position: relative;
  z-index: 2;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  animation: pop 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

.ripple {
  position: absolute;
  border-radius: 50%;
  border: 2px solid var(--color-primary);
  opacity: 0;
}

.ripple-1 {
  width: 64px;
  height: 64px;
  animation: ripple 1.5s ease-out infinite;
}

.ripple-2 {
  width: 64px;
  height: 64px;
  animation: ripple 1.5s ease-out 0.3s infinite;
}

.ripple-3 {
  width: 64px;
  height: 64px;
  animation: ripple 1.5s ease-out 0.6s infinite;
}

@keyframes pop {
  0% {
    transform: scale(0);
  }
  60% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes ripple {
  0% {
    transform: scale(1);
    opacity: 0.6;
  }
  100% {
    transform: scale(2);
    opacity: 0;
  }
}

.success-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--color-success);
}

/* 失败态（与成功态对称，醒目红色） */
.fail-anim {
  display: flex;
  justify-content: center;
}

.fail-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--color-danger);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 28px;
  animation: pop 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

.fail-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--color-danger);
}

.fail-reason {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  text-align: center;
}

.fail-tips {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.fail-tips-item {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.8;
}

.success-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.member-info {
  width: 100%;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.mi-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.mi-label {
  color: var(--color-text-secondary);
}

.mi-value {
  font-weight: 600;
  color: var(--color-dark);
}

.btn-continue {
  width: 100%;
  padding: 8px;
  background: var(--color-bg);
  color: var(--color-primary);
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
}

.success-pop-enter-active {
  transition: all 0.4s cubic-bezier(0.18, 0.89, 0.32, 1.28);
}

.success-pop-enter-from {
  opacity: 0;
  transform: scale(0.8);
}
</style>
