<template>
  <div class="camera-capture">
    <!-- 视频预览（拍照模式） -->
    <div class="video-wrap" v-show="authState === 'granted' && !captured">
      <video ref="videoRef" autoplay playsinline muted></video>
      <div class="face-guide">
        <div class="face-frame"></div>
        <p class="face-hint">将面部置于框内</p>
      </div>
    </div>

    <!-- 拍照后预览 -->
    <div class="preview-wrap" v-if="captured">
      <img :src="captured" alt="人脸照片" />
      <button class="btn-retake" @click="retake"><i class="fas fa-redo"></i> 重拍</button>
    </div>

    <!-- 取消授权 / 摄像头异常（pending 时仅显示授权弹窗，不展示此面板） -->
    <div class="permission-wrap" v-if="!captured && authState !== 'granted' && authState !== 'pending'">
      <i :class="['fas', permissionIcon]"></i>
      <p class="permission-title">{{ permissionTitle }}</p>
      <p class="permission-desc">{{ permissionDesc }}</p>
      <div class="permission-actions">
        <button
          class="btn-reauthorize"
          @click="requestPermission"
        >
          <i class="fas fa-camera"></i> 重新授权
        </button>
        <p class="upload-tip">或直接上传图片</p>
        <label class="btn-upload">
          <i class="fas fa-upload"></i> 上传图片
          <input type="file" accept="image/*" @change="onFileChange" hidden />
        </label>
      </div>
    </div>

    <!-- 拍照按钮 -->
    <button
      v-if="authState === 'granted' && !captured"
      class="btn-capture"
      @click="capture"
    >
      <i class="fas fa-camera"></i> 拍照采集
    </button>

    <!-- 摄像头启动中（挂载即请求，由浏览器原生权限框授权，无需二次确认） -->
    <div v-if="authState === 'pending'" class="camera-loading">
      <i class="fas fa-spinner fa-spin"></i>
      <span>正在启动摄像头…</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps<{
  /** 摄像头方向：user-前置，environment-后置 */
  facingMode?: 'user' | 'environment'
}>()

const emit = defineEmits<{
  (e: 'capture', imageBase64: string): void
}>()

/**
 * 授权状态机：
 * pending   - 等待用户在自定义弹窗中选择（未打开摄像头）
 * granted   - 已允许且摄像头启动成功
 * cancelled - 用户在弹窗中点了取消
 * denied    - 浏览器系统权限被拒绝
 * inuse     - 摄像头被其他程序占用
 * notfound  - 未检测到摄像头设备
 * unsupported- 当前环境不支持（非 HTTPS / 旧浏览器）
 * failed    - 其他启动异常
 */
type AuthState =
  | 'pending'
  | 'granted'
  | 'cancelled'
  | 'denied'
  | 'inuse'
  | 'notfound'
  | 'unsupported'
  | 'failed'

const videoRef = ref<HTMLVideoElement | null>(null)
const captured = ref<string | null>(null)
const authState = ref<AuthState>('pending')
let stream: MediaStream | null = null

// 组件挂载即请求摄像头权限：由浏览器原生权限框授权，不再二次弹窗确认
onMounted(() => {
  requestPermission()
})

const permissionIcon = computed(() => {
  switch (authState.value) {
    case 'cancelled': return 'fa-video-slash'
    case 'denied': return 'fa-lock'
    case 'inuse': return 'fa-circle-exclamation'
    case 'notfound': return 'fa-camera-rotate'
    case 'unsupported': return 'fa-triangle-exclamation'
    default: return 'fa-camera'
  }
})

const permissionTitle = computed(() => {
  switch (authState.value) {
    case 'cancelled': return '摄像头授权已取消'
    case 'denied': return '摄像头权限被拒绝'
    case 'inuse': return '摄像头被占用'
    case 'notfound': return '未检测到摄像头'
    case 'unsupported': return '当前环境不支持摄像头'
    default: return '摄像头启动失败'
  }
})

const permissionDesc = computed(() => {
  switch (authState.value) {
    case 'cancelled': return '您已取消摄像头授权，无法进行人脸识别'
    case 'denied': return '浏览器已拒绝摄像头权限，请在浏览器设置中允许'
    case 'inuse': return '摄像头被其他程序占用，请关闭后重试'
    case 'notfound': return '未检测到摄像头设备，请检查设备连接后重试'
    case 'unsupported': return '当前环境不支持摄像头，请使用 HTTPS 访问'
    default: return '摄像头启动失败，请点击重新授权再试一次'
  }
})

onUnmounted(() => {
  stopCamera()
})

/** 用户点击"允许" / "重新授权" */
const requestPermission = async () => {
  // 1. 环境能力检测（非 HTTPS 或旧浏览器下 navigator.mediaDevices 不存在）
  if (!navigator.mediaDevices || typeof navigator.mediaDevices.getUserMedia !== 'function') {
    authState.value = 'unsupported'
    return
  }

  try {
    const facing = props.facingMode || 'user'
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: facing, width: { ideal: 640 }, height: { ideal: 480 } },
      audio: false
    })
    authState.value = 'granted'
    if (videoRef.value) {
      videoRef.value.srcObject = stream
    } else {
      // granted 后 video 才渲染，等下一帧再绑定
      await nextTickBindStream()
    }
  } catch (e: any) {
    stopCamera()
    switch (e?.name) {
      case 'NotAllowedError':
      case 'SecurityError':
      case 'PermissionDeniedError':
        authState.value = 'denied'
        break
      case 'NotReadableError':
      case 'TrackStartError':
        authState.value = 'inuse'
        break
      case 'NotFoundError':
      case 'DevicesNotFoundError':
      case 'OverconstrainedError':
        authState.value = 'notfound'
        break
      default:
        authState.value = 'failed'
    }
  }
}

/** video 元素由 v-if/v-show 控制时，授权成功后等 DOM 渲染再绑定流 */
const nextTickBindStream = () => {
  requestAnimationFrame(() => {
    if (videoRef.value && stream) {
      videoRef.value.srcObject = stream
    }
  })
}

const stopCamera = () => {
  if (stream) {
    stream.getTracks().forEach((t) => t.stop())
    stream = null
  }
}

/** 拍照：canvas 截图 → 转 base64 */
const capture = () => {
  const video = videoRef.value
  if (!video) return
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth || 640
  canvas.height = video.videoHeight || 480
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
  // 输出 data URL（含 data:image/jpeg;base64, 前缀）
  const dataUrl = canvas.toDataURL('image/jpeg', 0.8)
  captured.value = dataUrl
  stopCamera()
  emit('capture', dataUrl)
}

/** 重拍：重新申请摄像头（已授权过则直接启动，不再次弹原生权限框） */
const retake = () => {
  captured.value = null
  requestPermission()
}

/** 上传图片处理（不依赖摄像头授权，保留原兜底通道） */
const onFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const dataUrl = reader.result as string
    captured.value = dataUrl
    emit('capture', dataUrl)
  }
  reader.readAsDataURL(file)
}
</script>

<style scoped>
.camera-capture {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
}

.video-wrap,
.preview-wrap {
  position: relative;
  width: 100%;
  max-width: 320px;
  aspect-ratio: 4 / 3;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: #000;
}

.video-wrap video,
.preview-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.face-guide {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.face-frame {
  width: 60%;
  aspect-ratio: 3 / 4;
  border: 2px solid rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  box-shadow: 0 0 0 100vmax rgba(0, 0, 0, 0.25);
}

.face-hint {
  margin-top: var(--spacing-sm);
  color: #fff;
  font-size: 12px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}

.btn-retake {
  position: absolute;
  bottom: var(--spacing-sm);
  right: var(--spacing-sm);
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 12px;
}

/* ==================== 授权前 / 异常提示面板 ==================== */
.permission-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
  text-align: center;
  width: 100%;
  max-width: 320px;
}

.permission-wrap > i {
  font-size: 40px;
  color: var(--color-text-placeholder);
}

.permission-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.permission-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.permission-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: var(--spacing-sm);
}

.btn-reauthorize {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 20px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: var(--shadow-active);
}

.upload-tip {
  color: var(--color-text-placeholder);
  font-size: 12px;
  margin-top: 4px;
}

.btn-upload {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 13px;
  cursor: pointer;
}

.btn-capture {
  padding: 10px 24px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-md);
  font-size: 15px;
  font-weight: 600;
  box-shadow: var(--shadow-active);
}

/* 摄像头启动中提示 */
.camera-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.camera-loading i {
  font-size: 16px;
  color: var(--color-primary);
}

/* ==================== 授权确认弹窗 ==================== */
.auth-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--spacing-md);
}

.auth-modal {
  width: 100%;
  max-width: 340px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
}

.auth-modal-head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 22px 20px 10px;
}

.auth-modal-head i {
  font-size: 30px;
  color: var(--color-primary);
}

.auth-modal-head h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.auth-modal-body {
  padding: 0 20px 18px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  text-align: center;
}

.auth-modal-footer {
  display: flex;
  border-top: 1px solid var(--color-border, #eee);
}

.auth-btn {
  flex: 1;
  padding: 13px 0;
  font-size: 15px;
  background: #fff;
  border: none;
  cursor: pointer;
}

.auth-btn + .auth-btn {
  border-left: 1px solid var(--color-border, #eee);
}

.auth-btn.cancel {
  color: var(--color-text-secondary);
}

.auth-btn.allow {
  color: var(--color-primary);
  font-weight: 600;
}

.auth-btn:active {
  background: var(--color-bg-gray, #f7f8fa);
}
</style>
