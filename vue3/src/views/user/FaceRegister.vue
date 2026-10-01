<template>
  <div class="face-register page-container">
    <PageHeader title="人脸录入" />
    <div class="page-body">
      <!-- 顶部说明 -->
      <section class="head card">
        <div class="head-icon">
          <i class="fas fa-user-shield"></i>
        </div>
        <div class="head-text">
          <h2 class="head-title">人脸录入</h2>
          <p class="head-sub">录入人脸，到店刷脸核销</p>
        </div>
      </section>

      <!-- 录入状态 -->
      <section class="status-bar card" v-if="faceRegistered && !submitting">
        <template v-if="faceMatchable">
          <i class="fas fa-check-circle status-icon-ok"></i>
          <span class="status-text-ok">已录入人脸</span>
        </template>
        <template v-else>
          <i class="fas fa-exclamation-triangle status-icon-warn"></i>
          <span class="status-text-warn">人脸底库不一致</span>
        </template>
        <button class="btn-re-register" @click="resetCapture">重新录入</button>
      </section>

      <!-- 登记存在但底库比对不上：必须显式告知，否则用户以为能刷脸却永远核销失败 -->
      <section class="hint-bar card" v-if="faceRegistered && !faceMatchable && !submitting">
        <i class="fas fa-info-circle"></i>
        <span>{{ faceHint || '当前识别模式下未找到你的人脸底库，请重新录入后才能刷脸核销' }}</span>
      </section>

      <!-- 注销人脸 -->
      <section class="danger-bar card" v-if="faceRegistered && !submitting">
        <span class="danger-text">不再使用刷脸核销？可注销已录入的人脸</span>
        <button class="btn-unregister" :disabled="unregistering" @click="unregister">
          {{ unregistering ? '处理中...' : '注销人脸' }}
        </button>
      </section>

      <!-- 摄像头采集 -->
      <section class="capture-section card" v-if="!faceRegistered || showCapture">
        <CameraCapture facing-mode="user" @capture="onCapture" />

        <!-- 提交按钮 -->
        <button
          v-if="imageBase64"
          class="btn-submit"
          :disabled="submitting"
          @click="submit"
        >
          <i class="fas fa-check" v-if="!submitting"></i>
          {{ submitting ? '提交中...' : '确认录入' }}
        </button>
      </section>

      <!-- 结果提示 -->
      <transition name="fade">
        <section class="result-card card" v-if="result">
          <div class="result-icon success">
            <i class="fas fa-check-circle"></i>
          </div>
          <div class="result-text">
            <p class="result-title">人脸录入成功</p>
            <p class="result-sub">人脸已绑定，到店刷脸即可核销</p>
          </div>
        </section>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import CameraCapture from '@/components/CameraCapture.vue'
import PageHeader from '@/components/PageHeader.vue'
import { userAPI } from '@/api/userAPI'
import { message } from '@/utils/message'
import { confirm } from '@/composables/useConfirm'

defineOptions({ name: 'UserFaceRegister' })

const router = useRouter()

const imageBase64 = ref<string>('')
const submitting = ref(false)
const faceRegistered = ref(false)
/** 当前识别模式下底库能否比对上（false = 登记与底库不一致，需重新录入） */
const faceMatchable = ref(true)
const faceHint = ref('')
const unregistering = ref(false)
const showCapture = ref(false)
const result = ref<{ faceToken: string; registered: boolean } | null>(null)

onMounted(async () => {
  await checkStatus()
})

const checkStatus = async () => {
  try {
    const res = await userAPI.getFaceStatus()
    if (res.success && res.data) {
      faceRegistered.value = !!res.data.registered
      // 登记存在 ≠ 能刷脸：切换识别模式后旧登记不在当前底库中，需提示重新录入
      faceMatchable.value = res.data.matchable !== false
      faceHint.value = res.data.hint || ''
      if (faceRegistered.value) showCapture.value = false
    }
  } catch {
    /* 静默 */
  }
}

const onCapture = (base64: string) => {
  imageBase64.value = base64
}

const resetCapture = () => {
  imageBase64.value = ''
  result.value = null
  showCapture.value = true
}

const submit = async () => {
  if (!imageBase64.value || submitting.value) return
  submitting.value = true
  try {
    const res = await userAPI.registerFace(imageBase64.value)
    if (res.success) {
      result.value = res.data
      faceRegistered.value = true
      faceMatchable.value = true
      faceHint.value = ''
      showCapture.value = false
      imageBase64.value = ''
    }
  } catch {
    /* request 拦截器已处理错误提示 */
  } finally {
    submitting.value = false
  }
}

/** 注销人脸：登记与识别底库一并清理 */
const unregister = async () => {
  const ok = await confirm({
    title: '注销人脸',
    content: '注销后将无法刷脸核销，需重新录入人脸才能恢复，确定继续吗？'
  })
  if (!ok) return
  unregistering.value = true
  try {
    const res = await userAPI.unregisterFace()
    if (res.success) {
      message.success(res.data?.message || '人脸已注销')
      faceRegistered.value = false
      faceMatchable.value = true
      faceHint.value = ''
      result.value = null
      imageBase64.value = ''
      showCapture.value = false
    }
  } catch {
    /* request 拦截器已处理错误提示 */
  } finally {
    unregistering.value = false
  }
}
</script>

<style scoped>
.face-register {
  min-height: 100vh;
  background: var(--color-bg);
}

.page-body {
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.head {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
}

.head-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  flex-shrink: 0;
}

.head-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-dark);
}

.head-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
}

.status-icon-ok {
  color: var(--color-success);
  font-size: 20px;
}

.status-text-ok {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-success);
}

.btn-re-register {
  padding: 6px 14px;
  background: var(--color-bg);
  color: var(--color-primary);
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.status-icon-warn {
  color: #fa8c16;
  font-size: 20px;
}

.status-text-warn {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #fa8c16;
}

.hint-bar {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  font-size: 13px;
  line-height: 1.6;
  color: #ad6800;
  background: #fffbe6;
}

.hint-bar i {
  color: #fa8c16;
  margin-top: 2px;
  flex-shrink: 0;
}

.danger-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
}

.danger-text {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.btn-unregister {
  padding: 6px 14px;
  background: #fff;
  color: #f5222d;
  border: 1px solid #f5222d;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.btn-unregister:disabled {
  opacity: 0.6;
}

.capture-section {
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
}

.btn-submit {
  width: 100%;
  padding: 12px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-md);
  font-size: 16px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.btn-submit:disabled {
  opacity: 0.6;
}

.result-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
}

.result-icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.result-icon.success {
  background: rgba(82, 196, 26, 0.12);
  color: var(--color-success);
}

.result-icon.info {
  background: rgba(24, 144, 255, 0.12);
  color: #1890ff;
}

.result-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-dark);
}

.result-sub {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
