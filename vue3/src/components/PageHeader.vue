<template>
  <div class="page-header-wrap">
    <header class="page-header">
      <button class="ph-back" aria-label="返回" @click="goBack">
        <i class="fas fa-chevron-left"></i>
      </button>
      <span class="ph-title">{{ title }}</span>
      <span class="ph-right">
        <slot name="right" />
      </span>
    </header>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

defineProps<{ title: string }>()

const router = useRouter()

const goBack = () => {
  // 有历史记录则后退，否则回到"我的"
  if (window.history.length > 1) router.back()
  else router.push('/user/profile')
}
</script>

<style scoped>
/* 顶部栏包装：占满屏幕宽度、sticky 置顶；
   背景放 wrap 上保证整行纯白，不出现左右留白露出页面底色 */
.page-header-wrap {
  position: sticky;
  top: 0;
  z-index: 20;
  flex-shrink: 0;
  width: 100%;
  background: var(--color-bg-white, #fff);
}

.page-header {
  display: flex;
  align-items: center;
  height: 44px;
  background: var(--color-bg-white, #fff);
  border-bottom: 1px solid var(--color-border);
  position: relative;
  flex-shrink: 0;
}

.ph-back {
  width: 44px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--color-text);
  font-size: 16px;
  cursor: pointer;
  flex-shrink: 0;
}

.ph-back:active {
  opacity: 0.6;
}

.ph-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 var(--spacing-xs);
}

.ph-right {
  min-width: 44px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: var(--spacing-sm);
  flex-shrink: 0;
}
</style>
