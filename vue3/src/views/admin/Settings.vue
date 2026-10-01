<template>
  <div class="settings-page">
    <h2 class="page-title">系统设置</h2>

    <div class="card form-section">
      <h3 class="section-title">账号信息</h3>
      <div class="form-row">
        <label class="form-label">管理员账号</label>
        <input class="form-input" type="text" value="管理员" disabled />
      </div>
      <div class="form-row">
        <label class="form-label">原密码</label>
        <input class="form-input" v-model="oldPwd" type="password" placeholder="请输入原密码" />
      </div>
      <div class="form-row">
        <label class="form-label">新密码</label>
        <input class="form-input" v-model="newPwd" type="password" placeholder="请输入新密码" />
      </div>
      <div class="form-row">
        <label class="form-label">确认新密码</label>
        <input class="form-input" v-model="confirmPwd" type="password" placeholder="再次输入新密码" />
      </div>
      <div class="form-row">
        <button class="btn-primary" @click="savePwd">保存密码</button>
      </div>
    </div>

    <div class="card form-section">
      <h3 class="section-title">系统参数</h3>
      <div class="form-row">
        <label class="form-label">健身房名称</label>
        <input class="form-input" v-model="gymName" type="text" placeholder="请输入健身房名称" />
      </div>
      <div class="form-row">
        <label class="form-label">到期提醒天数</label>
        <input class="form-input" v-model.number="expireDays" type="number" min="0" placeholder="会员卡到期前提醒天数" />
      </div>
      <div class="form-row">
        <label class="form-label">核销间隔(秒)</label>
        <input class="form-input" v-model.number="entryInterval" type="number" min="0" placeholder="同一会员卡两次核销最小间隔" />
      </div>
      <div class="form-row">
        <button class="btn-primary" @click="saveParams">保存参数</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from '@/utils/message'

// 账号信息
const oldPwd = ref('')
const newPwd = ref('')
const confirmPwd = ref('')

// 系统参数（空数据初始化，等后端接口返回再填充）
const gymName = ref('')
const expireDays = ref(0)
const entryInterval = ref(0)

const savePwd = () => {
  if (!oldPwd.value || !newPwd.value || !confirmPwd.value) {
    message.error('请填写完整密码信息')
    return
  }
  if (newPwd.value !== confirmPwd.value) {
    message.error('两次输入的新密码不一致')
    return
  }
  message.info('修改密码功能待后端接口接入')
}

const saveParams = () => {
  message.info('保存参数功能待后端接口接入')
}
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.page-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0;
}

.form-section {
  padding: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 var(--spacing-md);
}

.form-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.form-label {
  width: 100px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
  text-align: right;
}

.form-input {
  flex: 1;
  max-width: 280px;
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus {
  border-color: var(--color-primary);
}

.form-input:disabled {
  background-color: var(--color-bg-gray);
  color: var(--color-text-secondary);
  cursor: not-allowed;
}

.btn-primary {
  margin-left: 100px;
  padding: 8px 16px;
  background-color: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  transition: opacity 0.2s;
}

.btn-primary:hover {
  opacity: 0.9;
}
</style>
