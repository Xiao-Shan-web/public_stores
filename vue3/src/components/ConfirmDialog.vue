<template>
  <Transition name="confirm-fade">
    <div v-if="visible" class="confirm-mask" @click.self="handleCancel">
      <div class="confirm-modal">
        <div class="confirm-header">
          <span class="confirm-title">
            <i v-if="danger" class="fas fa-exclamation-triangle confirm-icon"></i>
            {{ title }}
          </span>
          <button class="confirm-close" @click="handleCancel">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="confirm-body">
          <p class="confirm-text">{{ content }}</p>
          <div v-if="inputLabel" class="confirm-input-wrap">
            <label class="confirm-input-label">{{ inputLabel }}</label>
            <input
              v-model="inputValue"
              class="confirm-input"
              :placeholder="inputPlaceholder"
              @keyup.enter="handleConfirm"
            />
          </div>
        </div>
        <div class="confirm-footer">
          <button class="btn-cancel" @click="handleCancel">{{ cancelText }}</button>
          <button
            class="btn-confirm"
            :class="{ 'btn-danger': danger }"
            @click="handleConfirm"
          >{{ confirmText }}</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { useConfirmDialog } from '@/composables/useConfirm'

const {
  visible,
  title,
  content,
  confirmText,
  cancelText,
  danger,
  inputLabel,
  inputPlaceholder,
  inputValue,
  handleConfirm,
  handleCancel,
} = useConfirmDialog()
</script>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.confirm-modal {
  width: 460px;
  max-width: 92vw;
  background-color: #fff;
  border-radius: var(--radius-md);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.confirm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
}

.confirm-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  display: flex;
  align-items: center;
  gap: 8px;
}

.confirm-icon {
  color: var(--color-danger);
  font-size: 15px;
}

.confirm-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--color-text-secondary);
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-close:hover {
  background-color: var(--color-bg-gray);
  color: var(--color-text);
}

.confirm-body {
  padding: 24px 20px;
}

.confirm-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
}

.confirm-input-wrap {
  margin-top: 14px;
}

.confirm-input-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.confirm-input {
  width: 100%;
  box-sizing: border-box;
  padding: 8px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.confirm-input:focus {
  border-color: var(--color-primary);
}

.confirm-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

.btn-cancel {
  padding: 8px 20px;
  background-color: var(--color-bg-gray);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  font-size: 14px;
  min-height: 36px;
  line-height: 1.5;
}

.btn-confirm {
  padding: 8px 20px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
  min-height: 36px;
  line-height: 1.5;
}

.btn-confirm.btn-danger {
  background: var(--color-danger);
}

/* 过渡动画 */
.confirm-fade-enter-active,
.confirm-fade-leave-active {
  transition: opacity 0.2s;
}

.confirm-fade-enter-active .confirm-modal,
.confirm-fade-leave-active .confirm-modal {
  transition: transform 0.2s cubic-bezier(0.18, 0.89, 0.32, 1.28), opacity 0.2s;
}

.confirm-fade-enter-from,
.confirm-fade-leave-to {
  opacity: 0;
}

.confirm-fade-enter-from .confirm-modal {
  transform: translateY(-12px) scale(0.96);
}

.confirm-fade-leave-to .confirm-modal {
  transform: translateY(-8px) scale(0.98);
}
</style>
