<template>
  <div class="cards">
    <div class="toolbar">
      <button class="btn-add" @click="openCreate">
        <i class="fas fa-plus"></i> 新建卡类型
      </button>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>卡名称</th>
            <th>分类</th>
            <th>适用范围</th>
            <th>节数</th>
            <th>有效天数</th>
            <th>价格(元)</th>
            <th>描述</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in list" :key="c.id">
            <td>{{ c.id }}</td>
            <td class="name-cell">{{ c.name }}</td>
            <td>
              <span class="tag" :class="c.category === 'PT' ? 'tag-pt' : 'tag-normal'">
                {{ c.category === 'PT' ? '私教课卡' : '普通卡' }}
              </span>
            </td>
            <td class="scope-cell">{{ scopeText(c) }}</td>
            <td>{{ c.category === 'PT' ? (c.totalTimes ?? '-') : '—' }}</td>
            <td>{{ c.durationDays }} 天</td>
            <td class="price-cell">¥{{ c.price }}</td>
            <td class="desc-cell">{{ c.description || '—' }}</td>
            <td>
              <span class="tag" :class="Number(c.isActive) === 1 ? 'tag-on' : 'tag-off'">
                {{ Number(c.isActive) === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="ops-cell">
              <button class="link-btn" @click="openEdit(c)">编辑</button>
              <button class="link-btn" @click="toggleStatus(c)">
                {{ Number(c.isActive) === 1 ? '禁用' : '启用' }}
              </button>
              <button class="link-btn danger" @click="removeCard(c)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="10" class="empty-row">暂无卡类型，点击右上角新建</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <div class="modal-mask" v-if="visible" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">{{ isEdit ? '编辑卡类型' : '新建卡类型' }}</span>
          <button class="modal-close" @click="closeModal"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>卡名称</label>
            <input
              v-model.trim="form.name"
              class="form-input"
              maxlength="100"
              placeholder="如：月卡 / 私教10次卡"
            />
          </div>
          <div class="form-row">
            <label class="form-label">
              <span class="req">*</span>分类
              <span v-if="isEdit" class="form-hint">（创建后不可修改）</span>
            </label>
            <select v-model="form.category" class="form-input" :disabled="isEdit">
              <option value="NORMAL">普通会员卡（月卡/季卡/半年卡/年卡）</option>
              <option value="PT">私教课卡（按节数）</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>适用范围</label>
            <select v-model="form.scope" class="form-input">
              <option value="ALL_STORE">全店通用</option>
              <option value="SINGLE_STORE">指定单店</option>
            </select>
          </div>
          <div class="form-row" v-if="form.scope === 'SINGLE_STORE'">
            <label class="form-label"><span class="req">*</span>绑定门店</label>
            <select v-model="form.storeId" class="form-input">
              <option :value="''" disabled>请选择门店</option>
              <option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="form-row" v-if="form.category === 'PT'">
            <label class="form-label"><span class="req">*</span>总节数</label>
            <input
              v-model.trim="form.totalTimes"
              class="form-input"
              type="number"
              min="1"
              placeholder="如：10 / 20 / 30"
            />
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>有效天数</label>
            <input
              v-model.trim="form.durationDays"
              class="form-input"
              type="number"
              min="1"
              placeholder="如：30（私教课卡为节数有效期）"
            />
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>价格(元)</label>
            <input
              v-model.trim="form.price"
              class="form-input"
              type="number"
              min="0"
              step="0.01"
              placeholder="如：199.00"
            />
          </div>
          <div class="form-row">
            <label class="form-label">卡描述</label>
            <textarea
              v-model.trim="form.description"
              class="form-input textarea"
              maxlength="500"
              placeholder="选填，简要描述该卡权益"
            ></textarea>
          </div>
          <div class="form-row switch-row">
            <label class="form-label">启用状态</label>
            <label class="switch">
              <input type="checkbox" :checked="Number(form.isActive) === 1" @change="onSwitchChange" />
              <span class="slider"></span>
              <span class="switch-text">{{ Number(form.isActive) === 1 ? '启用' : '禁用' }}</span>
            </label>
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
  getCardTypes,
  createCardType,
  updateCardType,
  deleteCardType,
  toggleCardTypeStatus,
  type CardType,
  type CardTypeSaveParams
} from '@/api/adminAPI'
import { getStores, type Store } from '@/api/storeAPI'
import { message } from '@/utils/message'
import { confirm } from '@/composables/useConfirm'

const list = ref<CardType[]>([])
const stores = ref<Store[]>([])
const loading = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const res = await getCardTypes()
    if (res.success) list.value = res.data?.list || []
  } catch (e) {
    /* 错误已由拦截器统一提示 */
  } finally {
    loading.value = false
  }
}

const loadStores = async () => {
  try {
    const res = await getStores()
    if (res.success) stores.value = res.data?.list || []
  } catch (e) {
    /* 静默 */
  }
}

/** 适用范围展示文本：全店通用 / 门店名 */
const scopeText = (c: CardType): string => {
  if (c.scope === 'SINGLE_STORE') return c.storeName || '指定门店'
  return '全店通用'
}

// ============ 弹窗（新建/编辑） ============
const visible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const editId = ref<number>(0)

interface FormState {
  name: string
  category: string
  scope: string
  storeId: number | ''
  totalTimes: string | number
  durationDays: string | number
  price: string | number
  description: string
  isActive: number
}

const form = reactive<FormState>({
  name: '',
  category: 'NORMAL',
  scope: 'ALL_STORE',
  storeId: '',
  totalTimes: '',
  durationDays: '',
  price: '',
  description: '',
  isActive: 1
})

const resetForm = () => {
  form.name = ''
  form.category = 'NORMAL'
  form.scope = 'ALL_STORE'
  form.storeId = ''
  form.totalTimes = ''
  form.durationDays = ''
  form.price = ''
  form.description = ''
  form.isActive = 1
}

const openCreate = () => {
  isEdit.value = false
  editId.value = 0
  resetForm()
  visible.value = true
}

const openEdit = (c: CardType) => {
  isEdit.value = true
  editId.value = c.id
  form.name = c.name
  form.category = c.category || 'NORMAL'
  form.scope = c.scope || 'ALL_STORE'
  form.storeId = c.storeId ?? ''
  form.totalTimes = c.totalTimes ?? ''
  form.durationDays = c.durationDays
  form.price = c.price
  form.description = c.description || ''
  form.isActive = c.isActive
  visible.value = true
}

const closeModal = () => {
  if (submitting.value) return
  visible.value = false
}

const onSwitchChange = (e: Event) => {
  form.isActive = (e.target as HTMLInputElement).checked ? 1 : 0
}

const validate = (): string | null => {
  if (!form.name) return '卡名称不能为空'
  const days = Number(form.durationDays)
  if (!Number.isFinite(days) || !Number.isInteger(days) || days <= 0) {
    return '有效天数需为正整数'
  }
  const price = Number(form.price)
  if (!Number.isFinite(price) || price < 0) return '价格格式不正确'
  if (form.scope === 'SINGLE_STORE' && !form.storeId) return '请选择绑定门店'
  if (form.category === 'PT') {
    const times = Number(form.totalTimes)
    if (!Number.isFinite(times) || !Number.isInteger(times) || times <= 0) {
      return '私教课卡需设置正整数节数'
    }
  }
  return null
}

const submit = async () => {
  const err = validate()
  if (err) {
    message.warning(err)
    return
  }

  const isPt = form.category === 'PT'
  const payload: CardTypeSaveParams = {
    name: form.name,
    category: form.category,
    scope: form.scope,
    storeId: form.scope === 'SINGLE_STORE' ? Number(form.storeId) : null,
    totalTimes: isPt ? Number(form.totalTimes) : null,
    durationDays: Number(form.durationDays),
    price: form.price,
    description: form.description || undefined,
    isActive: form.isActive
  }

  submitting.value = true
  try {
    if (isEdit.value) {
      const res = await updateCardType(editId.value, payload)
      if (res.success) {
        message.success('更新成功')
        await loadList()
        visible.value = false
      }
    } else {
      const res = await createCardType(payload)
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

// ============ 启用/禁用 ============
const toggleStatus = async (c: CardType) => {
  const next = Number(c.isActive) === 1 ? 0 : 1
  try {
    const res = await toggleCardTypeStatus(c.id, next)
    if (res.success) {
      c.isActive = next
      message.success(Number(next) === 1 ? '已启用' : '已禁用')
    }
  } catch (e) {
    /* 静默 */
  }
}

// ============ 删除（软删除） ============
const removeCard = async (c: CardType) => {
  if (!await confirm({ content: `确认删除卡类型「${c.name}」吗？删除后不可恢复。` })) return
  try {
    const res = await deleteCardType(c.id)
    if (res.success) {
      message.success('删除成功')
      await loadList()
    }
  } catch (e) {
    /* 静默 */
  }
}

onMounted(() => {
  loadList()
  loadStores()
})
</script>

<style scoped>
.cards {
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
}

.scope-cell {
  white-space: nowrap;
  color: var(--color-text-secondary);
}

.price-cell {
  color: var(--color-primary);
  font-weight: 600;
}

.desc-cell {
  color: var(--color-text-secondary);
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ops-cell {
  display: flex;
  gap: var(--spacing-xs);
  white-space: nowrap;
}

.tag {
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-on {
  background-color: rgba(103, 194, 58, 0.15);
  color: var(--color-success);
}

.tag-off {
  background-color: rgba(144, 147, 153, 0.15);
  color: var(--color-info);
}

.tag-normal {
  background-color: rgba(24, 144, 255, 0.12);
  color: #1890ff;
}

.tag-pt {
  background-color: rgba(255, 158, 0, 0.12);
  color: #ff9e00;
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

/* ============ 弹窗 ============ */
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
  max-height: 90vh;
  overflow-y: auto;
  background-color: #fff;
  border-radius: var(--radius-md);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
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

.form-input {
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-family: inherit;
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

.form-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-placeholder);
}

.textarea {
  resize: vertical;
  min-height: 76px;
  line-height: 1.5;
}

.switch-row {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.switch {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.switch input {
  display: none;
}

.slider {
  width: 40px;
  height: 22px;
  background-color: var(--color-text-placeholder);
  border-radius: 11px;
  position: relative;
  transition: background-color 0.2s;
}

.slider::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 16px;
  height: 16px;
  background-color: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}

.switch input:checked + .slider {
  background-color: var(--color-success);
}

.switch input:checked + .slider::after {
  transform: translateX(18px);
}

.switch-text {
  font-size: 13px;
  color: var(--color-text-secondary);
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
