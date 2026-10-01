<template>
  <!--
    全局图片放大预览遮罩（用户端 / 管理端共用）
    - Teleport 到 body，避免被父级 transform/overflow 裁切
    - 点击遮罩空白处关闭，点击图片本身不关闭，右上角关闭按钮关闭
    - 桌面端 Esc 关闭；移动端支持手指下滑超过阈值关闭（跟手位移 + 背景渐隐）
    - 图片加载中显示转圈；加载失败回退默认头像占位，杜绝破图白屏
  -->
  <Teleport to="body">
    <Transition name="ip-fade">
      <div v-if="previewUrl" class="ip-mask" :style="maskStyle" @click="close">
        <img
          v-if="!failed"
          :src="previewUrl"
          class="ip-img"
          :class="{ 'ip-dragging': dragging }"
          :style="imgStyle"
          alt="图片预览"
          draggable="false"
          @load="onLoad"
          @error="onError"
          @click.stop
          @touchstart.prevent="onTouchStart"
          @touchmove.prevent="onTouchMove"
          @touchend.prevent="onTouchEnd"
          @touchcancel.prevent="onTouchEnd"
        />
        <!-- 加载失败：回退默认头像占位（与 UserAvatar 同款 SVG） -->
        <div v-else class="ip-fallback" @click.stop>
          <svg viewBox="0 0 100 100" aria-hidden="true">
            <circle cx="50" cy="38" r="15" fill="currentColor" />
            <path d="M50 57c-15 0-27 10-27 26v17h54V83c0-16-12-26-27-26z" fill="currentColor" />
          </svg>
          <p>图片暂时无法查看</p>
        </div>
        <!-- 加载中提示 -->
        <div v-if="!loaded && !failed" class="ip-loading">
          <i class="fas fa-spinner fa-spin"></i>
        </div>
        <i class="fas fa-times ip-close" @click.stop="close"></i>
        <p v-if="!failed" class="ip-hint">下滑关闭</p>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useImagePreview } from '@/composables/useImagePreview'

const { previewUrl, close } = useImagePreview()

/** 图片加载状态（每次切换预览地址时重置） */
const loaded = ref(false)
const failed = ref(false)

const onLoad = () => { loaded.value = true }
const onError = () => { failed.value = true }

/* ==================== 移动端下滑关闭 ==================== */
/** 下滑关闭阈值（px），超过即关闭，否则松手回弹 */
const CLOSE_THRESHOLD = 100
const dragging = ref(false)
const dragY = ref(0)
let startY = 0

const onTouchStart = (e: TouchEvent) => {
  if (e.touches.length !== 1) return
  dragging.value = true
  startY = e.touches[0].clientY
}

const onTouchMove = (e: TouchEvent) => {
  if (!dragging.value || e.touches.length !== 1) return
  dragY.value = e.touches[0].clientY - startY
}

const onTouchEnd = () => {
  if (!dragging.value) return
  // 跟手下拉超过阈值 → 关闭；否则清空位移，图片回弹归位
  if (Math.abs(dragY.value) > CLOSE_THRESHOLD) {
    close()
  }
  dragging.value = false
  dragY.value = 0
}

/** 图片跟手：垂直位移 + 轻微缩放；拖拽中关闭过渡保证跟手，松手后 0.25s 回弹 */
const imgStyle = computed(() => {
  const y = dragY.value
  const scale = Math.max(0.8, 1 - Math.abs(y) / 800)
  return {
    transform: `translate3d(0, ${y}px, 0) scale(${scale})`,
    transition: dragging.value ? 'none' : 'transform 0.25s ease'
  }
})

/** 遮罩背景随下滑距离渐隐（0.85 → 最低 0.2） */
const maskStyle = computed(() => ({
  background: `rgba(0, 0, 0, ${Math.max(0.2, 0.85 - Math.abs(dragY.value) / 500)})`
}))

/* ==================== 打开 / 关闭副作用 ==================== */
// 切换图片时重置加载态与拖拽位移；打开期间锁定底层页面滚动
watch(previewUrl, (url) => {
  loaded.value = false
  failed.value = false
  dragging.value = false
  dragY.value = 0
  document.body.style.overflow = url ? 'hidden' : ''
})

/** 桌面端 Esc 关闭 */
const onKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape' && previewUrl.value) close()
}
window.addEventListener('keydown', onKeydown)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<style scoped>
.ip-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
}

.ip-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 6px;
  cursor: zoom-out;
  -webkit-user-select: none;
  user-select: none;
  /* 禁止浏览器默认滚动手势，由组件接管下滑关闭 */
  touch-action: none;
  will-change: transform;
}

/* 加载失败占位：与 UserAvatar 默认头像一致的 SVG 人像 */
.ip-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.65);
}

.ip-fallback svg {
  width: 110px;
  height: 110px;
}

.ip-fallback p {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.6);
}

/* 加载中转圈：不拦截点击，遮罩空白处仍可点关 */
.ip-loading {
  position: absolute;
  color: #fff;
  font-size: 30px;
  pointer-events: none;
}

.ip-close {
  position: absolute;
  top: 18px;
  right: 22px;
  font-size: 26px;
  color: #fff;
  cursor: pointer;
  opacity: 0.85;
}

.ip-close:hover {
  opacity: 1;
}

/* 下滑提示仅在触屏设备显示 */
.ip-hint {
  position: absolute;
  bottom: calc(22px + env(safe-area-inset-bottom, 0px));
  left: 0;
  right: 0;
  margin: 0;
  text-align: center;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  pointer-events: none;
  display: none;
}

@media (hover: none) and (pointer: coarse) {
  .ip-hint {
    display: block;
  }
}

/* 进出场淡入淡出 */
.ip-fade-enter-active,
.ip-fade-leave-active {
  transition: opacity 0.2s ease;
}

.ip-fade-enter-from,
.ip-fade-leave-to {
  opacity: 0;
}
</style>
