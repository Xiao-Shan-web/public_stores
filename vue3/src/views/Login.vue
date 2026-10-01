<template>
  <div class="login-page">
    <!-- 装饰圆 -->
    <div class="deco-circle deco-1"></div>
    <div class="deco-circle deco-2"></div>

    <div class="login-card">
      <div class="brand">
        <div class="brand-icon">
          <i :class="isAdmin ? 'fas fa-user-shield' : 'fas fa-dumbbell'"></i>
        </div>
        <h1 class="app-name">{{ isAdmin ? '山达后台' : '山达健身' }}</h1>
        <p class="slogan">{{ isAdmin ? '管理后台' : '燃动每一刻' }}</p>
      </div>

      <div class="form">
        <!-- 管理端：账号密码 -->
        <template v-if="isAdmin">
          <div class="field-input">
            <i class="fas fa-user"></i>
            <input
              ref="usernameInput"
              v-model="username"
              type="text"
              name="username"
              autocomplete="username"
              placeholder="管理员账号"
              @change="syncInputs"
              @focus="syncInputs"
              @animationstart="onAutofill"
              @keyup.enter="handleLogin"
            />
          </div>
          <div class="field-input">
            <i class="fas fa-lock"></i>
            <input
              ref="passwordInput"
              v-model="password"
              type="password"
              name="password"
              autocomplete="current-password"
              placeholder="密码"
              @change="syncInputs"
              @focus="syncInputs"
              @animationstart="onAutofill"
              @keyup.enter="handleLogin"
            />
          </div>
          <button
            class="btn-login"
            :disabled="loading"
            @click="handleLogin"
          >
            <i v-if="loading" class="fas fa-spinner fa-spin"></i>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </template>

        <!-- 用户端：手机号一键登录 -->
        <template v-else>
          <div class="field-input" :class="{ 'field-error': phone && !phoneValid }">
            <i class="fas fa-mobile-alt"></i>
            <input
              ref="phoneInput"
              v-model="phone"
              type="tel"
              name="phone"
              autocomplete="tel"
              maxlength="11"
              inputmode="numeric"
              placeholder="请输入手机号"
              @change="syncInputs"
              @focus="syncInputs"
              @animationstart="onAutofill"
              @keyup.enter="handleLoginClick"
            />
          </div>
          <p v-if="phone && !phoneValid" class="field-tip">请输入正确的手机号</p>

          <button
            class="btn-login"
            :disabled="loading"
            @click="handleLoginClick"
          >
            <i v-if="loading" class="fas fa-spinner fa-spin"></i>
            <span>{{ loading ? '登录中...' : '一键登录' }}</span>
          </button>

          <!-- 协议勾选（仅用户端） -->
          <label class="agree-row">
            <input type="checkbox" v-model="agreed" />
            <span class="agree-text">
              我已阅读并同意
              <a @click.stop="showAgreement = true">《用户协议》</a>
              和
              <a @click.stop="showAgreement = true">《隐私政策》</a>
            </span>
          </label>
        </template>
      </div>
    </div>

    <!-- 用户协议弹窗（仅用户端） -->
    <div v-if="!isAdmin && showAgreement" class="modal-mask" @click.self="showAgreement = false">
      <div class="modal-card">
        <div class="modal-header">
          <h3>用户协议</h3>
          <i class="fas fa-times modal-close" @click="showAgreement = false"></i>
        </div>

        <div class="modal-body">
          <h4 class="ag-title">1. 服务说明</h4>
          <p class="ag-text">山达健身会员系统提供会员卡购买、激活、入场核销等服务。</p>

          <h4 class="ag-title">2. 会员卡使用规则</h4>
          <ul class="ag-list">
            <li>会员卡仅限本人使用</li>
            <li>入场时需进行人脸识别验证</li>
            <li>会员卡过期后需续费才能继续使用</li>
          </ul>

          <h4 class="ag-title">3. 人脸信息授权</h4>
          <ul class="ag-list">
            <li>为保障场馆安全，需采集用户人脸信息</li>
            <li>人脸信息仅用于入场核销</li>
            <li>数据加密存储，不向第三方泄露</li>
          </ul>

          <h4 class="ag-title">4. 隐私保护</h4>
          <ul class="ag-list">
            <li>手机号仅用于账号登录</li>
            <li>个人信息严格保密</li>
          </ul>

          <h4 class="ag-title">5. 免责声明</h4>
          <ul class="ag-list">
            <li>用户需遵守场馆规章制度</li>
            <li>因个人原因造成的损失自负</li>
          </ul>
        </div>

        <div class="modal-footer">
          <label class="agree-row modal-agree">
            <input type="checkbox" v-model="agreed" />
            <span class="agree-text">我已阅读并同意《用户协议》和《隐私政策》</span>
          </label>
          <div class="modal-btns">
            <button class="btn-cancel" @click="showAgreement = false">不同意</button>
            <button class="btn-confirm" :disabled="!agreed" @click="agreeAndLogin">同意并登录</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { authAPI } from '@/api/authAPI'
import message from '@/utils/message'
import heroBgUrl from '@/static/images/山达健身会员卡背景5.webp'
import cardThumbBgUrl from '@/static/images/山达健身会员卡背景6.webp'

// 登录成功后预加载首页关键图：用 new Image() 提前触发请求，
// 与路由切换并行，首页挂载时图已在浏览器缓存，避免一张张渐进加载。
// 引用挂在模块级数组上，防止 GC 回收中断未完成请求。
const preloadedImages: HTMLImageElement[] = []
const preloadHomeImages = () => {
  for (const url of [heroBgUrl, cardThumbBgUrl]) {
    const img = new Image()
    img.src = url
    preloadedImages.push(img)
  }
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 根据 redirect 参数判断用户端 / 管理端
// 兜底：无 redirect 但本地登录类型为 admin（如直接访问 /login）时，也展示管理员表单
const isAdmin = computed(() => {
  const redirect = (route.query.redirect as string) || ''
  return redirect.startsWith('/admin') || (!redirect && authStore.userType === 'admin')
})

// 用户端表单
const phone = ref('')
// 管理端表单
const username = ref('')
const password = ref('')

// 输入框 DOM 引用：用于同步浏览器密码管理器的自动填充值
const phoneInput = ref<HTMLInputElement | null>(null)
const usernameInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)

const loading = ref(false)
const showAgreement = ref(false)
const agreed = ref(localStorage.getItem('agreed') === 'true')

/**
 * 浏览器自动填充（密码管理器 / Chrome autofill）经常只写 DOM value，
 * 不触发 v-model 依赖的 input 事件，导致响应式变量为空、登录按钮一直禁用。
 * 这里在多种时机从真实 DOM 回填一次响应式值。
 */
const syncInputs = () => {
  if (phoneInput.value) phone.value = phoneInput.value.value
  if (usernameInput.value) username.value = usernameInput.value.value
  if (passwordInput.value) password.value = passwordInput.value.value
}

// -webkit-autofill 命中时会触发 animationstart（见下方样式），此时立即同步
const onAutofill = () => {
  setTimeout(syncInputs, 0)
}

// 页面从 bfcache 恢复 / 切回标签页时，自动填充可能已变化，补一次同步
const onPageShow = () => syncInputs()

// 登录页挂载后的有限探测：autofill 写入时机不确定（可能早于 300ms，也可能数秒后），
// 3 秒内每 250ms 从 DOM 同步一次；检测到已填充或超时即停止，不做长期轮询。
let autofillPoll: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  let tries = 0
  autofillPoll = setInterval(() => {
    tries++
    syncInputs()
    const filled = isAdmin.value
      ? !!(username.value && password.value)
      : !!phone.value
    if (filled || tries >= 12) {
      if (autofillPoll) clearInterval(autofillPoll)
      autofillPoll = null
    }
  }, 250)
  window.addEventListener('pageshow', onPageShow)
})

onBeforeUnmount(() => {
  if (autofillPoll) clearInterval(autofillPoll)
  window.removeEventListener('pageshow', onPageShow)
})

// 中国大陆手机号规则：11 位、1 开头、第二位 3-9
const PHONE_REGEX = /^1[3-9]\d{9}$/
// 实时校验：输入过程中提示格式错误
const phoneValid = computed(() => PHONE_REGEX.test(phone.value))

// 用户端登录按钮点击：已同意直接登录，否则弹协议
const handleLoginClick = () => {
  if (agreed.value) {
    handleLogin()
  } else {
    showAgreement.value = true
  }
}

const agreeAndLogin = () => {
  agreed.value = true
  localStorage.setItem('agreed', 'true')
  showAgreement.value = false
  handleLogin()
}

// 统一登录处理：根据 isAdmin 调用不同接口
const handleLogin = async () => {
  // 防重复提交：请求未结束前不再进入，finally 中保证 loading 一定复位
  if (loading.value) return
  if (isAdmin.value) {
    // 管理端：账号密码登录
    if (!username.value || !password.value) {
      message.error('请输入账号和密码')
      return
    }
    loading.value = true
    try {
      const res = await authAPI.adminLogin({
        username: username.value,
        password: password.value
      })
      if (res.success) {
        authStore.setToken(res.data.token, res.data.expiresAt, 'admin')
        authStore.setUserInfo(res.data.adminInfo)
        message.success('登录成功')
        const redirect = (route.query.redirect as string) || '/admin/dashboard'
        router.replace(redirect)
      } else {
        message.error(res.message || '登录失败')
      }
    } catch (e: any) {
      message.error(e.message || '登录失败')
    } finally {
      loading.value = false
    }
  } else {
    // 用户端：手机号一键登录
    if (!phoneValid.value) {
      message.warning('请输入正确的手机号')
      return
    }
    loading.value = true
    try {
      const res = await authAPI.login({ phone: phone.value })
      if (res.success) {
        authStore.setToken(res.data.token, res.data.expiresAt, 'user')
        authStore.setUserInfo(res.data.userInfo)
        // 预加载首页关键图（与 router.replace 并行）
        preloadHomeImages()
        message.success('登录成功，燃动开启！')
        const redirect = (route.query.redirect as string) || '/user/home'
        router.replace(redirect)
      } else {
        message.error(res.message || '登录失败')
      }
    } catch (e: any) {
      message.error(e.message || '登录失败')
    } finally {
      loading.value = false
    }
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-lg);
  background: var(--gradient-primary);
  overflow: hidden;
}

/* 装饰圆 */
.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.deco-1 {
  width: 260px;
  height: 260px;
  top: -80px;
  right: -60px;
}

.deco-2 {
  width: 180px;
  height: 180px;
  bottom: -60px;
  left: -50px;
}

.login-card {
  position: relative;
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.2);
}

.brand {
  text-align: center;
  margin-bottom: var(--spacing-xl);
}

.brand-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto var(--spacing-md);
  border-radius: 50%;
  background: var(--gradient-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32px;
  box-shadow: var(--shadow-active);
}

.app-name {
  font-size: 30px;
  font-weight: 800;
  color: var(--color-dark);
  letter-spacing: 1px;
}

.slogan {
  margin-top: var(--spacing-xs);
  font-size: 15px;
  color: var(--color-primary);
  font-weight: 600;
  letter-spacing: 2px;
}

/* 通用输入框（手机号 / 账号 / 密码共用） */
.field-input {
  display: flex;
  align-items: center;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0 var(--spacing-md);
  margin-bottom: var(--spacing-md);
  background: var(--color-bg-gray);
  transition: border-color 0.2s;
}

.field-input:focus-within {
  border-color: var(--color-primary);
  background: #fff;
}

.field-input i {
  color: var(--color-primary);
  margin-right: var(--spacing-sm);
}

.field-input input {
  flex: 1;
  border: none;
  outline: none;
  padding: 14px 0;
  font-size: 16px;
  background: transparent;
  letter-spacing: 1px;
}

/*
 * 浏览器自动填充检测：命中 -webkit-autofill 时触发一次 animationstart，
 * JS 借此把 autofill 写入的 DOM 值同步给 v-model
 * （Chrome 等浏览器的密码管理器自动填充不保证派发 input 事件）
 */
@keyframes autofill-detected {
  from { opacity: 1; }
  to { opacity: 1; }
}

.field-input input:-webkit-autofill {
  animation-name: autofill-detected;
  animation-duration: 0.01s;
}

/* 实时校验错误态 */
.field-input.field-error {
  border-color: var(--color-danger, #f5222d);
  background: #fff;
}

.field-tip {
  margin: -4px 2px 8px;
  font-size: 12px;
  color: var(--color-danger, #f5222d);
  line-height: 1.4;
}

.btn-login {
  width: 100%;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-md);
  padding: 15px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
  box-shadow: var(--shadow-active);
  transition: transform 0.15s;
  margin-top: var(--spacing-xs);
}

.btn-login:active {
  transform: scale(0.98);
}

.btn-login:disabled {
  background: var(--color-text-placeholder);
  box-shadow: none;
  cursor: not-allowed;
}

/* 协议勾选行 */
.agree-row {
  display: flex;
  align-items: flex-start;
  margin-top: var(--spacing-md);
  cursor: pointer;
  user-select: none;
}

.agree-row input[type='checkbox'] {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  margin-right: var(--spacing-sm);
  accent-color: var(--color-primary);
  cursor: pointer;
}

.agree-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.agree-text a {
  color: var(--color-primary);
  cursor: pointer;
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
  max-height: 85vh;
  background: #fff;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--color-border);
}

.modal-header h3 {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-dark);
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
}

.ag-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-dark);
  margin-top: var(--spacing-md);
}

.ag-title:first-child {
  margin-top: 0;
}

.ag-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-top: var(--spacing-xs);
}

.ag-list {
  margin-top: var(--spacing-xs);
  padding-left: var(--spacing-md);
}

.ag-list li {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.7;
  list-style: disc;
}

.modal-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--color-border);
}

.modal-agree {
  margin-top: 0;
  margin-bottom: var(--spacing-md);
}

.modal-btns {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-cancel {
  flex: 1;
  background: var(--color-bg-gray);
  color: var(--color-text-secondary);
  border-radius: var(--radius-md);
  padding: 12px;
  font-size: 15px;
}

.btn-confirm {
  flex: 1;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-md);
  padding: 12px;
  font-size: 15px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.btn-confirm:disabled {
  background: var(--color-text-placeholder);
  box-shadow: none;
  cursor: not-allowed;
}
</style>
