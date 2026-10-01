<template>
  <div class="profile-edit-page">
    <PageHeader title="个人资料" />

    <div class="page-body">
      <!--
        头像区：
        - 大头像点击弹出全局预览（上传中则预览本地压缩图，上传后预览服务器新图）
        - 右下角相机角标 / 下方「更换头像」按钮均可触发选图
        - 状态行展示当前头像状态；上传中头像显示遮罩转圈，成功后立即刷新
      -->
      <div class="avatar-card card">
        <div class="avatar-big">
          <UserAvatar
            :src="localPreview || resolveAvatar(form.avatar)"
            previewable
          />
          <button
            type="button"
            class="avatar-camera"
            :disabled="uploading"
            aria-label="更换头像"
            @click.stop="chooseAvatar"
          >
            <i :class="uploading ? 'fas fa-spinner fa-spin' : 'fas fa-camera'"></i>
          </button>
          <span v-if="uploading" class="avatar-mask"><i class="fas fa-spinner fa-spin"></i></span>
        </div>
        <p class="avatar-status">{{ avatarStatusText }}</p>
        <button type="button" class="change-btn" :disabled="uploading" @click="chooseAvatar">
          <i class="fas fa-image"></i>&nbsp;{{ uploading ? '上传中…' : '更换头像' }}
        </button>
        <p class="avatar-tip">支持 jpg / png / webp，大小不超过 20MB；点击头像可查看大图</p>
        <input
          ref="fileInputRef"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          class="hidden-file"
          @change="onFileChange"
        />
      </div>

      <!-- 基本信息 -->
      <div class="form-card card">
        <div class="form-row">
          <label class="row-label" for="nickname">昵称</label>
          <input
            id="nickname"
            v-model="form.nickname"
            class="row-input"
            type="text"
            maxlength="100"
            placeholder="请输入昵称"
          />
        </div>
        <div class="form-row">
          <label class="row-label" for="realName">真实姓名</label>
          <input
            id="realName"
            v-model="form.realName"
            class="row-input"
            type="text"
            maxlength="50"
            placeholder="请输入真实姓名"
          />
        </div>
        <div class="form-row">
          <span class="row-label">性别</span>
          <div class="gender-group">
            <button
              v-for="g in genderOptions"
              :key="g.value"
              type="button"
              class="gender-btn"
              :class="{ active: form.gender === g.value }"
              @click="form.gender = g.value"
            >{{ g.label }}</button>
          </div>
        </div>
        <div class="form-row">
          <label class="row-label" for="birthday">生日</label>
          <input
            id="birthday"
            v-model="form.birthday"
            class="row-input"
            type="date"
            :max="today"
          />
        </div>
        <div class="form-row form-row-column">
          <label class="row-label" for="bio">个人简介</label>
          <textarea
            id="bio"
            v-model="form.bio"
            class="bio-input"
            rows="3"
            maxlength="200"
            placeholder="介绍一下自己吧"
          ></textarea>
          <span class="bio-count">{{ (form.bio || '').length }}/200</span>
        </div>
      </div>

      <button class="save-btn" :disabled="saving || loading" @click="handleSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { userAPI, type Gender } from '@/api/userAPI'
import { useAuthStore } from '@/stores/auth'
import message from '@/utils/message'

const router = useRouter()
const authStore = useAuthStore()

const genderOptions: { label: string; value: Gender }[] = [
  { label: '男', value: 'MALE' },
  { label: '女', value: 'FEMALE' },
  { label: '保密', value: 'UNKNOWN' }
]

const today = new Date().toISOString().substring(0, 10)

const form = reactive({
  avatar: '' as string,
  nickname: '',
  realName: '',
  gender: 'UNKNOWN' as Gender,
  birthday: '',
  bio: ''
})

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
/** 选图压缩后的本地预览地址（上传完成或离开页面前释放） */
const localPreview = ref('')

/** 当前头像状态文案：上传中 / 已设置（可点预览）/ 未设置 */
const avatarStatusText = computed(() => {
  if (uploading.value) return '头像上传中…'
  if (localPreview.value || form.avatar) return '当前头像 · 点击头像可查看大图'
  return '暂未设置头像'
})

/** 进入页面加载当前资料 */
onMounted(async () => {
  loading.value = true
  try {
    const res = await userAPI.getProfile()
    if (res.success && res.data) {
      form.avatar = res.data.avatar || ''
      form.nickname = res.data.nickname || ''
      form.realName = res.data.realName || ''
      form.gender = res.data.gender || 'UNKNOWN'
      form.birthday = res.data.birthday || ''
      form.bio = res.data.bio || ''
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
})

const chooseAvatar = () => {
  if (uploading.value) return
  fileInputRef.value?.click()
}

/**
 * 前端 Canvas 压缩：等比缩放到最大边长 512，透明铺白底，转 JPEG q0.8。
 * 用于 >10MB 的大图，减小上传体积，避免阻塞网络。
 */
const compressImage = (file: File): Promise<Blob> => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      const maxEdge = 512
      const scale = Math.max(img.width, img.height) > maxEdge
        ? maxEdge / Math.max(img.width, img.height)
        : 1
      const w = Math.max(1, Math.round(img.width * scale))
      const h = Math.max(1, Math.round(img.height * scale))
      const canvas = document.createElement('canvas')
      canvas.width = w
      canvas.height = h
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('压缩失败'))
        return
      }
      // 透明通道铺白底，避免 JPEG 黑底
      ctx.fillStyle = '#fff'
      ctx.fillRect(0, 0, w, h)
      ctx.drawImage(img, 0, 0, w, h)
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('压缩失败'))),
        'image/jpeg',
        0.8
      )
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片加载失败'))
    }
    img.src = url
  })
}

/** 选择图片 → 必要时前端压缩 → 上传 → 即时刷新头像 */
const onFileChange = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 清空 value，保证选择同一文件能再次触发 change
  input.value = ''
  if (!file) return

  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    message.error('仅支持 jpg/png/webp 格式')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    message.error('图片过大，请选择 20MB 以内的图片')
    return
  }

  uploading.value = true
  try {
    let payload: File = file
    // 超过 10MB 前端先压缩，降低上传体积
    if (file.size > 10 * 1024 * 1024) {
      try {
        const blob = await compressImage(file)
        payload = new File([blob], 'avatar.jpg', { type: 'image/jpeg' })
      } catch {
        message.error('压缩失败，请重新上传')
        return
      }
    }

    // 压缩后立即显示本地预览，避免上传过程中头像无变化
    if (localPreview.value) URL.revokeObjectURL(localPreview.value)
    localPreview.value = URL.createObjectURL(payload)

    const res = await userAPI.uploadAvatar(payload)
    if (res.success && res.data?.avatar) {
      form.avatar = res.data.avatar
      // 同步到全局登录信息（avatar + avatarThumb 同时更新）：
      // 首页/我的页/客服聊天用 avatar，列表/评论用 avatarThumb；
      // 两字段都更新保证所有页面立即拿到带 ?v= 版本号的新地址，
      // 响应式触发 UserAvatar 重新加载，无需重新请求用户信息。
      authStore.patchUserInfo({
        avatar: res.data.avatar,
        avatarThumb: res.data.avatarThumb
      })
      // 释放本地预览，切回服务器 URL 显示：
      // 上传成功时新头像已落库且 URL 带 ?v=，浏览器会发起新请求；
      // 此前本地预览已让用户无等待感，现在切换可保证离开页面后
      // 直接命中浏览器已下载的图片，不会再次等待。
      if (localPreview.value) {
        URL.revokeObjectURL(localPreview.value)
        localPreview.value = ''
      }
      message.success('头像上传成功')
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    uploading.value = false
  }
}

/** 离开页面前释放本地预览地址，避免内存泄漏 */
onBeforeUnmount(() => {
  if (localPreview.value) {
    URL.revokeObjectURL(localPreview.value)
    localPreview.value = ''
  }
})

/** 保存资料 */
const handleSave = async () => {
  if (saving.value) return
  const nickname = form.nickname.trim()
  const realName = form.realName.trim()
  const bio = form.bio.trim()
  if (nickname.length > 100) {
    message.error('昵称最多 100 个字符')
    return
  }
  if (realName.length > 50) {
    message.error('真实姓名最多 50 个字符')
    return
  }

  saving.value = true
  try {
    const res = await userAPI.updateProfile({
      nickname: nickname || null,
      realName: realName || null,
      gender: form.gender,
      birthday: form.birthday || null,
      bio: bio || null
    })
    if (res.success) {
      // 全局登录信息同步最新昵称/头像，保证首页、我的页展示一致
      authStore.patchUserInfo({
        nickname: nickname || null,
        avatar: form.avatar || null
      })
      message.success('保存成功')
      router.back()
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-edit-page {
  min-height: 100vh;
  background: var(--color-bg);
}

.page-body {
  padding: var(--spacing-md);
}

/* ==================== 头像区 ==================== */
.avatar-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 16px 20px;
  margin-bottom: var(--spacing-md);
}

/* 大头像（88px，原行内 56px 加大）：点击放大预览，加载失败由 UserAvatar 回退默认头像。
   不设 overflow:hidden，保证右下角相机角标可以探出圆边；圆形裁剪由 UserAvatar/遮罩自身圆角完成 */
.avatar-big {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: var(--color-bg-gray);
  color: var(--color-text-placeholder);
}

/* 上传中遮罩：半透明黑 + 转圈，覆盖头像拦截预览点击 */
.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  z-index: 2;
}

/* 右下角相机角标：探出圆边 2px，点击触发更换头像 */
.avatar-camera {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid #fff;
  background: var(--gradient-primary);
  color: #fff;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 3;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.avatar-camera:disabled {
  opacity: 0.8;
}

.avatar-status {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.change-btn {
  margin-top: 10px;
  padding: 8px 22px;
  border-radius: 18px;
  border: 1px solid var(--color-primary);
  background: #fff;
  color: var(--color-primary);
  font-size: 13px;
}

.change-btn:disabled {
  opacity: 0.6;
}

.avatar-tip {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--color-text-placeholder);
}

.hidden-file {
  display: none;
}

/* ==================== 表单卡片 ==================== */
.form-card {
  padding: 0 var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.form-row {
  display: flex;
  align-items: center;
  min-height: 52px;
  border-bottom: 1px solid var(--color-border);
  gap: var(--spacing-md);
}

.form-row:last-child {
  border-bottom: none;
}

.form-row-column {
  flex-direction: column;
  align-items: stretch;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
}

.row-label {
  flex: 0 0 72px;
  font-size: 14px;
  color: var(--color-text);
}

.row-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-text);
  text-align: right;
  padding: 8px 0;
}

/* 性别分段选择 */
.gender-group {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.gender-btn {
  min-width: 52px;
  padding: 6px 12px;
  border-radius: 16px;
  border: 1px solid var(--color-border);
  background: #fff;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.gender-btn.active {
  background: var(--gradient-primary);
  border-color: transparent;
  color: #fff;
  font-weight: 600;
}

/* 个人简介 */
.bio-input {
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm);
  font-size: 14px;
  color: var(--color-text);
  background: var(--color-bg);
  outline: none;
  resize: none;
  font-family: inherit;
  box-sizing: border-box;
}

.bio-input:focus {
  border-color: var(--color-primary);
  background: #fff;
}

.bio-count {
  align-self: flex-end;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

/* ==================== 保存按钮 ==================== */
.save-btn {
  width: 100%;
  padding: 13px 0;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-md);
  font-size: 16px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

.save-btn:active {
  transform: scale(0.98);
}

.save-btn:disabled {
  opacity: 0.6;
}
</style>
