<template>
  <div class="notifications-page">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <button class="btn-add" @click="openCreate">
        <i class="fas fa-plus"></i> 新建通知
      </button>
    </div>

    <!-- 已发通知列表 -->
    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>通知标题</th>
            <th>通知类型</th>
            <th>发送范围</th>
            <th>接收人数</th>
            <th>发送时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.batchId">
            <td class="title-cell">{{ row.title }}</td>
            <td>
              <span class="tag" :class="typeTagClass(row.type)">{{ typeText(row.type) }}</span>
            </td>
            <td>
              <span class="tag" :class="scopeTagClass(row.scope)">
                {{ scopeText(row) }}
              </span>
            </td>
            <td>{{ row.receiverCount }} 人</td>
            <td class="time-cell">{{ formatTime(row.createdAt) }}</td>
            <td>
              <button class="link-btn danger" @click="remove(row)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="6" class="empty-row">暂无系统通知，点击右上角「新建通知」发送</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 新建通知弹窗 -->
    <div class="modal-mask" v-if="visible" @click.self="closeModal">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">新建系统通知</span>
          <button class="modal-close" @click="closeModal"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>通知标题</label>
            <input
              v-model.trim="form.title"
              class="form-input"
              maxlength="128"
              placeholder="请输入通知标题"
            />
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>通知内容</label>
            <textarea
              v-model.trim="form.content"
              class="form-input textarea"
              maxlength="500"
              placeholder="请输入通知内容"
            ></textarea>
          </div>
          <div class="form-row">
            <label class="form-label">通知类型</label>
            <select v-model="form.type" class="form-input">
              <option value="SYSTEM">系统公告</option>
              <option value="ACTIVITY">活动通知</option>
              <option value="VERIFY">审核通知</option>
              <option value="EXPIRE">到期提醒</option>
            </select>
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>发送范围</label>
            <div class="scope-switch">
              <button
                type="button"
                class="scope-opt"
                :class="{ active: form.scope === 'ALL' }"
                @click="form.scope = 'ALL'"
              >
                全部用户
              </button>
              <button
                type="button"
                class="scope-opt"
                :class="{ active: form.scope === 'SEGMENT' }"
                @click="form.scope = 'SEGMENT'"
              >
                分人群
              </button>
              <button
                type="button"
                class="scope-opt"
                :class="{ active: form.scope === 'SPECIFIED' }"
                @click="form.scope = 'SPECIFIED'"
              >
                指定用户
              </button>
            </div>
          </div>

          <!-- 分人群：卡类型 / 门店筛选 + 实时触达人数 -->
          <div class="form-row" v-if="form.scope === 'SEGMENT'">
            <label class="form-label">筛选条件（不选则发给全部用户）</label>
            <div class="segment-grid">
              <select v-model="form.cardTypeId" class="form-input">
                <option :value="null">不限卡类型</option>
                <option v-for="c in cardTypes" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
              <select v-model="form.storeId" class="form-input">
                <option :value="null">不限门店</option>
                <option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }}</option>
              </select>
            </div>
            <div class="audience-tip" :class="{ loading: previewLoading }">
              <i class="fas fa-users"></i>
              <template v-if="previewLoading">正在统计触达人数...</template>
              <template v-else-if="preview">
                预计触达 <b>{{ preview.segmentCount }}</b> 人
                <span class="audience-sub">
                  （全部用户 {{ preview.totalUsers }} 人<template v-if="preview.cardTypeCount !== null">，持该卡 {{ preview.cardTypeCount }} 人</template><template v-if="preview.storeCount !== null">，该门店卡 {{ preview.storeCount }} 人</template>）
                </span>
              </template>
              <template v-else>选择筛选条件后显示预计触达人数</template>
            </div>
          </div>

          <div class="form-row" v-if="form.scope === 'SPECIFIED'">
            <label class="form-label">接收用户（用户ID 或 手机号）</label>
            <textarea
              v-model.trim="form.receivers"
              class="form-input textarea receivers"
              placeholder="多个用户用逗号或换行分隔，如：13800000000, 123456789"
            ></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="closeModal">取消</button>
          <button class="btn-confirm" :disabled="submitting" @click="submit">
            {{ submitting ? '发送中...' : '发送' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import {
  adminNotificationAPI,
  type AdminNotificationBatch,
  type AudiencePreview,
  type CreateNotificationPayload
} from '@/api/messageAPI'
import { getCardTypes, type CardType } from '@/api/adminAPI'
import { getStores, type Store } from '@/api/storeAPI'
import message from '@/utils/message'
import { confirm } from '@/composables/useConfirm'

const list = ref<AdminNotificationBatch[]>([])
const loading = ref(false)

// ==================== 新建弹窗 ====================
const visible = ref(false)
const submitting = ref(false)

type Scope = 'ALL' | 'SEGMENT' | 'SPECIFIED'

const form = ref<{
  type: string
  title: string
  content: string
  scope: Scope
  receivers: string
  cardTypeId: number | null
  storeId: number | null
}>({
  type: 'SYSTEM',
  title: '',
  content: '',
  scope: 'ALL',
  receivers: '',
  cardTypeId: null,
  storeId: null
})

// 分人群选项与预览
const cardTypes = ref<CardType[]>([])
const stores = ref<Store[]>([])
const preview = ref<AudiencePreview | null>(null)
const previewLoading = ref(false)

const loadOptions = async () => {
  if (cardTypes.value.length || stores.value.length) return
  try {
    const [cards, storeRes] = await Promise.all([getCardTypes(), getStores()])
    if (cards.success) cardTypes.value = cards.data?.list || []
    if (storeRes.success) stores.value = storeRes.data?.list || []
  } catch {
    /* 下拉加载失败不阻塞发送，拦截器已统一提示 */
  }
}

/** 拉取分人群触达预览（防抖，避免快速切换下拉时刷接口） */
let previewTimer: ReturnType<typeof setTimeout> | null = null
const loadPreview = () => {
  if (previewTimer) clearTimeout(previewTimer)
  previewTimer = setTimeout(async () => {
    if (form.value.scope !== 'SEGMENT') return
    previewLoading.value = true
    try {
      const res = await adminNotificationAPI.audiencePreview({
        cardTypeId: form.value.cardTypeId,
        storeId: form.value.storeId
      })
      if (res.success) preview.value = res.data
    } catch {
      /* 预览失败静默处理，不打断填写 */
    } finally {
      previewLoading.value = false
    }
  }, 260)
}

watch(
  () => [form.value.scope, form.value.cardTypeId, form.value.storeId],
  () => {
    if (form.value.scope === 'SEGMENT') {
      loadPreview()
    } else {
      preview.value = null
    }
  }
)

const loadList = async () => {
  loading.value = true
  try {
    const res = await adminNotificationAPI.list({ page: 1, size: 100 })
    if (res.success) list.value = res.data?.list || []
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  form.value = {
    type: 'SYSTEM',
    title: '',
    content: '',
    scope: 'ALL',
    receivers: '',
    cardTypeId: null,
    storeId: null
  }
  preview.value = null
  visible.value = true
  loadOptions()
}

const closeModal = () => {
  visible.value = false
}

/** 解析指定用户输入：按逗号/分号/空白分隔，去重去空 */
const parseReceivers = (): string[] => {
  const tokens = form.value.receivers.split(/[\s,，;；]+/).map(s => s.trim()).filter(Boolean)
  return Array.from(new Set(tokens))
}

const submit = async () => {
  if (!form.value.title) {
    message.warning('请输入通知标题')
    return
  }
  if (!form.value.content) {
    message.warning('请输入通知内容')
    return
  }

  const payload: CreateNotificationPayload = {
    type: form.value.type,
    title: form.value.title,
    content: form.value.content,
    scope: form.value.scope
  }
  if (form.value.scope === 'SPECIFIED') {
    const receivers = parseReceivers()
    if (!receivers.length) {
      message.warning('请填写至少一个用户ID或手机号')
      return
    }
    payload.receivers = receivers
  } else if (form.value.scope === 'SEGMENT') {
    // 分人群：至少给一个条件，否则与「全部用户」等价，提示避免误发
    if (form.value.cardTypeId === null && form.value.storeId === null) {
      message.warning('分人群发送请至少选择卡类型或门店；如需全量发送请选「全部用户」')
      return
    }
    payload.cardTypeId = form.value.cardTypeId
    payload.storeId = form.value.storeId
  }

  submitting.value = true
  try {
    const res = await adminNotificationAPI.create(payload)
    if (res.success) {
      message.success(res.message || '发送成功')
      visible.value = false
      loadList()
    } else {
      // 业务失败（如未匹配到用户）：HTTP 200，success=false
      message.error(res.message || '发送失败')
    }
  } catch {
    /* 拦截器已统一提示 */
  } finally {
    submitting.value = false
  }
}

const remove = async (row: AdminNotificationBatch) => {
  if (!await confirm({ content: `确认删除通知「${row.title}」吗？删除后不可恢复。` })) return
  try {
    const res = await adminNotificationAPI.remove(row.batchId)
    if (res.success) {
      message.success(res.message || '已删除')
      list.value = list.value.filter(item => item.batchId !== row.batchId)
    }
  } catch {
    /* 拦截器已统一提示 */
  }
}

// ==================== 展示辅助 ====================
const formatTime = (t: string) => {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}

const typeText = (type: string) => {
  const map: Record<string, string> = {
    SYSTEM: '系统公告',
    ACTIVITY: '活动通知',
    VERIFY: '审核通知',
    EXPIRE: '到期提醒',
    INTERACT: '互动通知',
    AI_PLAN: 'AI计划'
  }
  return map[type] || type
}

const typeTagClass = (type: string) => {
  const map: Record<string, string> = {
    SYSTEM: 'tag-system',
    ACTIVITY: 'tag-activity',
    VERIFY: 'tag-verify',
    EXPIRE: 'tag-expire'
  }
  return map[type] || 'tag-system'
}

/** 发送范围文案：分人群时拼接筛选条件，便于回溯 */
const scopeText = (row: AdminNotificationBatch) => {
  if (row.scope === 'ALL') return '全部用户'
  if (row.scope === 'SEGMENT') {
    const parts: string[] = []
    const card = cardTypes.value.find(c => c.id === row.cardTypeId)
    const store = stores.value.find(s => s.id === row.storeId)
    if (row.cardTypeId) parts.push(card ? card.name : `卡类型#${row.cardTypeId}`)
    if (row.storeId) parts.push(store ? store.name : `门店#${row.storeId}`)
    return parts.length ? `分人群 · ${parts.join(' / ')}` : '分人群'
  }
  return '指定用户'
}

const scopeTagClass = (scope?: string) => {
  if (scope === 'ALL') return 'tag-all'
  if (scope === 'SEGMENT') return 'tag-segment'
  return 'tag-spec'
}

onMounted(() => {
  loadList()
  // 选项仅用于列表回显筛选条件名称，进入页面即预加载
  loadOptions()
})
</script>

<style scoped>
.notifications-page {
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
  white-space: nowrap;
}

.title-cell {
  font-weight: 600;
  color: var(--color-text);
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time-cell {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.tag {
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-all {
  background-color: rgba(24, 144, 255, 0.12);
  color: #1890ff;
}

.tag-spec {
  background-color: rgba(255, 158, 0, 0.12);
  color: #ff9e00;
}

.tag-segment {
  background-color: rgba(114, 46, 209, 0.12);
  color: #722ed1;
}

.tag-system {
  background-color: rgba(24, 144, 255, 0.12);
  color: #1890ff;
}

.tag-activity {
  background-color: rgba(235, 47, 150, 0.12);
  color: #eb2f96;
}

.tag-verify {
  background-color: rgba(82, 196, 26, 0.12);
  color: #52c41a;
}

.tag-expire {
  background-color: rgba(250, 140, 22, 0.12);
  color: #fa8c16;
}

.link-btn {
  padding: 3px 6px;
  background-color: rgba(255, 77, 79, 0.08);
  color: var(--color-danger);
  font-size: 13px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
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
  max-height: 70vh;
  overflow-y: auto;
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

.textarea {
  resize: vertical;
  min-height: 76px;
  line-height: 1.5;
}

.receivers {
  min-height: 76px;
}

/* 发送范围切换 */
.scope-switch {
  display: flex;
  gap: 10px;
}

.scope-opt {
  flex: 1;
  height: 36px;
  padding: 0;
  font-size: 14px;
  line-height: 1.5;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background-color: #fff;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.scope-opt.active {
  border-color: var(--color-primary);
  background-color: rgba(255, 107, 53, 0.08);
  color: var(--color-primary);
  font-weight: 600;
}

/* 分人群筛选 */
.segment-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.audience-tip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  padding: 7px 11px;
  border-radius: var(--radius-sm);
  background-color: rgba(24, 144, 255, 0.07);
  color: #1890ff;
  font-size: 13px;
  line-height: 1.6;
}

.audience-tip b {
  font-size: 15px;
  padding: 0 3px;
}

.audience-tip.loading {
  color: var(--color-text-secondary);
  background-color: var(--color-bg-gray);
}

.audience-sub {
  color: var(--color-text-secondary);
  font-size: 12px;
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
