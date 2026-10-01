<template>
  <div class="stores">
    <div class="toolbar">
      <button class="btn-add" @click="openCreate">
        <i class="fas fa-plus"></i> 新建门店
      </button>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>门店名称</th>
            <th>门店地址</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in list" :key="s.id">
            <td>{{ s.id }}</td>
            <td class="name-cell">{{ s.name }}</td>
            <td class="address-cell">{{ s.address || '—' }}</td>
            <td class="ops-cell">
              <button class="link-btn" @click="openEdit(s)">编辑</button>
              <button class="link-btn danger" @click="removeStore(s)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="4" class="empty-row">暂无门店，点击右上角新建</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <div class="modal-mask" v-if="visible" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">{{ isEdit ? '编辑门店' : '新建门店' }}</span>
          <button class="modal-close" @click="closeModal"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>门店名称</label>
            <input
              v-model.trim="form.name"
              class="form-input"
              maxlength="100"
              placeholder="如：南山旗舰店"
            />
          </div>
          <div class="form-row">
            <label class="form-label">门店地址</label>
            <textarea
              v-model.trim="form.address"
              class="form-input textarea"
              maxlength="255"
              placeholder="选填，如：深圳市南山区科技园示范路1号"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="closeModal">取消</button>
          <button class="btn-confirm" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中...' : (isEdit ? '保存' : '新建') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  getStores,
  createStore,
  updateStore,
  deleteStore,
  type Store
} from '@/api/storeAPI'
import { message } from '@/utils/message'
import { confirm } from '@/composables/useConfirm'

defineOptions({ name: 'AdminStores' })

const list = ref<Store[]>([])

const loadList = async () => {
  try {
    const res = await getStores()
    if (res.success) list.value = res.data?.list || []
  } catch (e) {
    /* 错误已由拦截器统一提示 */
  }
}

// ============ 弹窗（新建/编辑） ============
const visible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editId = ref<number>(0)

const form = reactive({
  name: '',
  address: ''
})

const resetForm = () => {
  form.name = ''
  form.address = ''
}

const openCreate = () => {
  isEdit.value = false
  editId.value = 0
  resetForm()
  visible.value = true
}

const openEdit = (s: Store) => {
  isEdit.value = true
  editId.value = s.id
  form.name = s.name
  form.address = s.address || ''
  visible.value = true
}

const closeModal = () => {
  if (submitting.value) return
  visible.value = false
}

const submit = async () => {
  if (!form.name) {
    message.warning('门店名称不能为空')
    return
  }
  if (form.name.length > 100) {
    message.warning('门店名称不能超过100字')
    return
  }

  const payload = { name: form.name, address: form.address || null }
  submitting.value = true
  try {
    if (isEdit.value) {
      const res = await updateStore(editId.value, payload)
      if (res.success) {
        message.success('更新成功')
        await loadList()
        visible.value = false
      }
    } else {
      const res = await createStore(payload)
      if (res.success) {
        message.success('新建成功')
        await loadList()
        visible.value = false
      }
    }
  } catch (e) {
    /* 错误已由拦截器统一提示 */
  } finally {
    submitting.value = false
  }
}

// ============ 删除（软删除） ============
const removeStore = async (s: Store) => {
  if (!await confirm({
    title: '删除确认',
    content: `确认删除门店「${s.name}」吗？已被卡类型绑定的门店无法删除。`,
    confirmText: '确认删除',
    danger: true
  })) return
  try {
    const res = await deleteStore(s.id)
    if (res.success) {
      message.success('删除成功')
      await loadList()
    }
  } catch (e) {
    /* 静默 */
  }
}

onMounted(loadList)
</script>

<style scoped>
.stores {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.toolbar {
  display: flex;
  justify-content: flex-end;
}

.btn-add {
  padding: 8px 16px;
  background-color: var(--color-success);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th,
.data-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.data-table th {
  color: var(--color-text-secondary);
  font-weight: 600;
  background-color: var(--color-bg);
}

.name-cell {
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
}

.address-cell {
  color: var(--color-text-secondary);
  max-width: 360px;
}

.ops-cell {
  display: flex;
  gap: var(--spacing-xs);
  white-space: nowrap;
}

.link-btn {
  padding: 3px 6px;
  background-color: rgba(255, 107, 53, 0.08);
  color: var(--color-primary);
  font-size: 13px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.link-btn.danger {
  background-color: rgba(255, 77, 79, 0.08);
  color: var(--color-danger);
}

.empty-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 20px 0;
}

/* ============ 弹窗（与卡类型页统一 460px 规范） ============ */
.modal-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  width: 460px;
  max-width: 92vw;
  background-color: #fff;
  border-radius: var(--radius-md);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  animation: modalIn 0.2s ease;
}

@keyframes modalIn {
  from { transform: translateY(-12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--color-text-secondary);
  font-size: 15px;
}

.modal-close:hover {
  background-color: var(--color-bg-gray);
  color: var(--color-text);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.req {
  color: var(--color-danger);
  margin-right: 2px;
}

.textarea {
  resize: vertical;
  min-height: 76px;
  line-height: 1.5;
}

.modal-footer {
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
}

.btn-confirm {
  padding: 8px 20px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
