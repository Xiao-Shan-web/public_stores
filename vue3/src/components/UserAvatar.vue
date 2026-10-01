<template>
  <!--
    全局统一用户头像组件
    - 有 src 且未加载失败 → 显示头像图片（100% 填充父容器）
    - 无 src 或加载失败 → 显示默认头像（内联 SVG 人像占位）
    - previewable=true 时点击图片弹出全局放大预览（预览地址取 previewSrc，缺省回退 src）
    - SVG 用 currentColor 继承父容器 color，宽度高度 100% 填充父容器
    - 父容器只需控制尺寸 / 圆角 / 背景色 / color，即可适配各页面
  -->
  <img
    v-if="src && !failed"
    :src="src"
    class="ua-img"
    :class="{ 'ua-previewable': previewable }"
    alt="头像"
    loading="lazy"
    @error="failed = true"
    @click="onClick"
  />
  <svg v-else class="ua-fallback" viewBox="0 0 100 100" aria-hidden="true">
    <circle cx="50" cy="38" r="15" fill="currentColor" />
    <path d="M50 57c-15 0-27 10-27 26v17h54V83c0-16-12-26-27-26z" fill="currentColor" />
  </svg>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useImagePreview } from '@/composables/useImagePreview'

const props = defineProps<{
  /** 头像地址：有值显示头像，空/null 显示默认占位，加载失败自动回退默认 */
  src?: string | null
  /** 是否允许点击放大预览（默认 false，不影响仅展示场景） */
  previewable?: boolean
  /** 预览用地址：列表展示可用缩略图 src，预览传主图 previewSrc；缺省回退 src */
  previewSrc?: string | null
}>()

const failed = ref(false)
const { open } = useImagePreview()

// src 变化时重置失败态，重新尝试加载（切换用户 / 更新头像后 URL 变化）
watch(() => props.src, () => { failed.value = false })

const onClick = () => {
  if (!props.previewable || !props.src) return
  open(props.previewSrc || props.src)
}
</script>

<style scoped>
/* 头像统一圆形：组件层强制圆角，不依赖父容器 overflow 裁剪 */
.ua-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 50%;
}

/* 可预览头像：放大镜光标，提示可点击 */
.ua-previewable {
  cursor: zoom-in;
}

.ua-fallback {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: 50%;
}
</style>
