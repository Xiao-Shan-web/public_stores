<template>
  <div class="activity-page">
    <div class="page-head">
      <h2 class="page-title">限时活动</h2>
      <button class="primary-btn" @click="openModal()">
        <i class="fas fa-plus"></i> 新建活动
      </button>
    </div>

    <!-- 活动列表 -->
    <div class="act-grid">
      <div class="act-card card" v-for="a in list" :key="a.id">
        <div class="act-top">
          <div class="act-title-wrap">
            <h3 class="act-title">{{ a.title }}</h3>
            <p class="act-sub" v-if="a.subtitle">{{ a.subtitle }}</p>
          </div>
        </div>

        <div class="act-body">
          <div class="act-line">
            <span class="label">适用卡类型</span>
            <span class="value">{{ a.cardTypeName || '卡类型已删除' }}</span>
          </div>
          <div class="act-line">
            <span class="label">折扣</span>
            <span class="value">
              <span class="discount">{{ discountText(a.discount) }}</span>
              <span class="price-old" v-if="a.originalPrice">原价 ¥{{ a.originalPrice }}</span>
              <span class="price-new" v-if="a.activityPrice">活动价 ¥{{ a.activityPrice }}</span>
            </span>
          </div>
          <div class="act-line">
            <span class="label">活动时间</span>
            <span class="value small">{{ fmtTime(a.startTime) }} ~ {{ fmtTime(a.endTime) }}</span>
          </div>
          <div class="act-line">
            <span class="label">名额</span>
            <span class="value">
              {{ a.quotaTotal == null ? '不限名额' : `${a.quotaUsed} / ${a.quotaTotal}` }}
            </span>
          </div>
        </div>

        <div class="act-foot">
          <div class="tag-group">
            <span class="tag" :class="Number(a.status) === 1 ? 'tag-success' : 'tag-muted'">
              {{ Number(a.status) === 1 ? '已上架' : '已下架' }}
            </span>
            <span class="tag" :class="phaseClass(a.phase)">{{ phaseText(a.phase) }}</span>
            <span
              class="tag"
              :class="Number(a.userVisible) === 1 ? 'tag-visible' : 'tag-hidden'"
              title="仅当活动已上架、当前在活动时间内且名额未满时，用户端才会展示"
            >
              {{ Number(a.userVisible) === 1 ? '用户可见' : '用户不可见' }}
            </span>
          </div>
          <div class="foot-btns">
            <button class="link-btn" @click="openModal(a)">编辑</button>
            <button class="link-btn" @click="toggle(a)">{{ Number(a.status) === 1 ? '下架' : '上架' }}</button>
            <button class="link-btn danger" @click="remove(a)">删除</button>
          </div>
        </div>
      </div>

      <div v-if="!list.length" class="empty-state card">
        <i class="fas fa-bullhorn"></i>
        <p>暂无活动，点击右上角新建</p>
      </div>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <div v-if="showModal" class="modal-mask" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-head">
          <h3>{{ form.id ? '编辑活动' : '新建活动' }}</h3>
          <button class="modal-close" @click="showModal = false"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label>活动标题 <em>*</em></label>
            <input v-model.trim="form.title" maxlength="40" placeholder="如：双节同庆 · 年卡 8 折" />
          </div>
          <div class="form-row">
            <label>副标题</label>
            <input v-model.trim="form.subtitle" maxlength="60" placeholder="如：限时 3 天，先到先得" />
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label>适用卡类型 <em>*</em></label>
              <select v-model="form.cardTypeId">
                <option :value="null" disabled>请选择</option>
                <option v-for="c in cardTypes" :key="c.id" :value="c.id">
                  {{ c.name }}（¥{{ c.price }}）
                </option>
              </select>
            </div>
            <div class="form-row">
              <label>折扣率 <em>*</em></label>
              <input v-model="form.discount" type="number" min="0.01" max="0.99" step="0.01" placeholder="0.9 表示 9 折" />
            </div>
          </div>
          <div class="preview-line" v-if="pricePreview">
            <i class="fas fa-calculator"></i>
            活动价预览：<b>¥{{ pricePreview }}</b>（原价 ¥{{ selectedPrice }}）
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label>活动名额</label>
              <input v-model="form.quotaTotal" type="number" min="0" placeholder="留空表示不限" />
            </div>
            <div class="form-row">
              <label>状态</label>
              <select v-model="form.status">
                <option :value="1">上架</option>
                <option :value="0">下架</option>
              </select>
            </div>
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label>开始时间 <em>*</em></label>
              <input v-model="form.startTime" type="datetime-local" />
            </div>
            <div class="form-row">
              <label>结束时间 <em>*</em></label>
              <input v-model="form.endTime" type="datetime-local" />
            </div>
          </div>
          <div class="form-row">
            <label>活动详情</label>
            <textarea v-model="form.content" rows="3" placeholder="活动说明（选填）"></textarea>
          </div>
        </div>
        <div class="modal-foot">
          <button class="ghost-btn" @click="showModal = false">取消</button>
          <button class="primary-btn" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { message } from '@/utils/message'
import { confirm } from '@/composables/useConfirm'
import {
  adminGetActivities, adminCreateActivity, adminUpdateActivity,
  adminToggleActivity, adminDeleteActivity
} from '@/api/operationAPI'
import type { ActivityItem, CardTypeOption } from '@/api/operationAPI'
import { getCardTypes } from '@/api/adminAPI'

const list = ref<ActivityItem[]>([])
const cardTypes = ref<CardTypeOption[]>([])

const loadList = async () => {
  try {
    const res = await adminGetActivities()
    if (res.success && res.data?.list) list.value = res.data.list
  } catch { /* 拦截器已提示 */ }
}

const loadCardTypes = async () => {
  try {
    const res = await getCardTypes()
    if (res.success && res.data?.list) {
      cardTypes.value = res.data.list
        .filter(c => Number(c.isActive) === 1)
        .map(c => ({ id: c.id, name: c.name, price: c.price, category: c.category }))
    }
  } catch { /* 静默 */ }
}

// ==================== 弹窗表单 ====================
const showModal = ref(false)
const saving = ref(false)

const defaultRange = () => {
  const now = new Date()
  const end = new Date(now.getTime() + 7 * 24 * 3600 * 1000)
  const fmt = (d: Date) => {
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
  }
  return { start: fmt(now), end: fmt(end) }
}

const emptyForm = () => {
  const r = defaultRange()
  return {
    id: null as number | null,
    title: '',
    subtitle: '',
    cardTypeId: null as number | null,
    discount: '0.90',
    quotaTotal: null as number | null,
    startTime: r.start,
    endTime: r.end,
    content: '',
    status: 1
  }
}

const form = ref(emptyForm())

const selectedPrice = computed(() => {
  const c = cardTypes.value.find(x => x.id === form.value.cardTypeId)
  return c?.price ?? ''
})

/** 活动价预览（服务端最终以同样公式计算） */
const pricePreview = computed(() => {
  const price = Number(selectedPrice.value)
  const d = Number(form.value.discount)
  if (!price || !d || d <= 0 || d >= 1) return ''
  return (price * d).toFixed(2)
})

const openModal = (a?: ActivityItem) => {
  if (a) {
    form.value = {
      id: a.id,
      title: a.title,
      subtitle: a.subtitle || '',
      cardTypeId: a.cardTypeId,
      discount: String(a.discount),
      quotaTotal: a.quotaTotal,
      startTime: (a.startTime || '').substring(0, 16),
      endTime: (a.endTime || '').substring(0, 16),
      content: a.content || '',
      status: a.status ?? 1
    }
  } else {
    form.value = emptyForm()
  }
  showModal.value = true
}

const save = async () => {
  const f = form.value
  if (!f.title) return message.error('请填写活动标题')
  if (!f.cardTypeId) return message.error('请选择适用卡类型')
  const d = Number(f.discount)
  if (!d || d <= 0 || d >= 1) return message.error('折扣率需在 0 与 1 之间（如 0.9）')
  if (!f.startTime || !f.endTime) return message.error('请填写活动开始与结束时间')
  if (new Date(f.endTime) <= new Date(f.startTime)) return message.error('结束时间必须晚于开始时间')

  const payload = {
    title: f.title,
    subtitle: f.subtitle || null,
    content: f.content || null,
    cardTypeId: f.cardTypeId,
    discount: d,
    quotaTotal: f.quotaTotal === null || f.quotaTotal === undefined ? null : Number(f.quotaTotal),
    startTime: f.startTime,
    endTime: f.endTime,
    status: f.status
  }

  saving.value = true
  try {
    const res = f.id
      ? await adminUpdateActivity(f.id, payload as any)
      : await adminCreateActivity(payload as any)
    if (res.success) {
      message.success(f.id ? '修改成功' : '创建成功')
      showModal.value = false
      loadList()
    }
    // 失败原因由 request 响应拦截器统一 toast（HTTP 200 + success=false 也会提示）
  } catch { /* 网络/HTTP 异常已由拦截器提示 */ } finally {
    saving.value = false
  }
}

const toggle = async (a: ActivityItem) => {
  const toUp = Number(a.status) !== 1
  try {
    const res = await adminToggleActivity(a.id, toUp ? 1 : 0)
    if (res.success) {
      // 优先展示后端返回的提示（例如"已上架；活动将于 xx 开始，用户端在开始时间之后才会展示"）
      message.success(res.message || (toUp ? '已上架' : '已下架'))
      loadList()
    }
  } catch { /* 网络/HTTP 异常已由拦截器提示 */ }
}

const remove = async (a: ActivityItem) => {
  const ok = await confirm({
    title: '删除活动',
    content: `确定删除「${a.title}」吗？删除后用户端不再展示该活动。`
  })
  if (!ok) return
  try {
    const res = await adminDeleteActivity(a.id)
    if (res.success) {
      message.success('已删除')
      loadList()
    }
  } catch { /* 网络/HTTP 异常已由拦截器提示 */ }
}

// ==================== 展示工具 ====================
/**
 * 活动阶段：后端按 NOW() 计算并以字符串枚举下发（ONGOING/UPCOMING/ENDED）。
 * 这里用 String() 明确类型后做严格比较，避免空值/异常值影响展示。
 */
const phaseText = (p?: string) => {
  switch (String(p ?? '')) {
    case 'UPCOMING': return '未开始'
    case 'ENDED': return '已结束'
    case 'ONGOING': return '进行中'
    default: return '-'
  }
}
const phaseClass = (p?: string) => {
  switch (String(p ?? '')) {
    case 'UPCOMING': return 'tag-upcoming'
    case 'ENDED': return 'tag-ended'
    case 'ONGOING': return 'tag-ongoing'
    default: return 'tag-muted'
  }
}

const discountText = (d: string | number) => {
  const n = Number(d)
  if (!n) return '-'
  const tenths = n * 10
  return Math.abs(tenths - Math.round(tenths)) < 0.01
    ? `${Math.round(tenths)} 折`
    : `${tenths.toFixed(1)} 折`
}

const fmtTime = (t: string) => (t ? t.replace('T', ' ').substring(0, 16) : '-')

onMounted(() => {
  loadList()
  loadCardTypes()
})
</script>

<style scoped>
.activity-page { display: flex; flex-direction: column; gap: var(--spacing-md); }

.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-title { font-size: 18px; font-weight: 700; color: var(--color-text); }

.primary-btn {
  background: var(--gradient-primary);
  color: #fff;
  border: none;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.primary-btn:disabled { opacity: 0.6; cursor: not-allowed; }

.ghost-btn {
  background: #fff;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

/* 活动卡片网格 */
.act-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: var(--spacing-md);
}

.act-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.act-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.act-title-wrap { min-width: 0; }
.act-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.act-sub {
  font-size: 12px;
  color: var(--color-text-placeholder);
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.act-body { display: flex; flex-direction: column; gap: 9px; }

.act-line { display: flex; align-items: baseline; gap: 10px; font-size: 13px; }
.act-line .label { color: var(--color-text-placeholder); flex-shrink: 0; min-width: 68px; }
.act-line .value { color: var(--color-text); }
.act-line .value.small { font-size: 12px; color: var(--color-text-secondary); }

.discount {
  color: var(--color-primary);
  font-weight: 700;
  margin-right: 8px;
}
.price-old {
  font-size: 12px;
  color: var(--color-text-placeholder);
  text-decoration: line-through;
  margin-right: 8px;
}
.price-new {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-danger, #ff4d4f);
}

.act-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.foot-btns { display: flex; gap: 4px; }
.foot-btns .link-btn { color: var(--color-primary); }
.foot-btns .link-btn.danger { color: var(--color-danger, #ff4d4f); }

.tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  flex-shrink: 0;
}
.tag-success { background: rgba(82, 196, 26, 0.12); color: var(--color-success, #52c41a); }
.tag-muted { background: rgba(0, 0, 0, 0.06); color: var(--color-text-secondary); }

/* 阶段 + 用户端可见性标签 */
.tag-group { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.tag-ongoing { background: rgba(24, 144, 255, 0.12); color: #1890ff; }
.tag-upcoming { background: rgba(250, 140, 22, 0.12); color: #fa8c16; }
.tag-ended { background: rgba(0, 0, 0, 0.06); color: var(--color-text-secondary); }
.tag-visible { background: rgba(82, 196, 26, 0.12); color: var(--color-success, #52c41a); }
.tag-hidden { background: rgba(0, 0, 0, 0.06); color: var(--color-text-secondary); }

.empty-state { text-align: center; padding: 40px 0; color: var(--color-text-placeholder); }
.empty-state i { font-size: 34px; margin-bottom: 10px; }

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal {
  background: #fff;
  border-radius: var(--radius-md, 10px);
  width: 100%;
  max-width: 580px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.modal-head h3 { font-size: 16px; font-weight: 700; color: var(--color-text); }
.modal-close { background: none; border: none; color: var(--color-text-secondary); font-size: 16px; cursor: pointer; padding: 4px; }

.modal-body {
  padding: 18px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-row { display: flex; flex-direction: column; gap: 6px; }
.form-row label { font-size: 13px; color: var(--color-text-secondary); }
.form-row label em { color: var(--color-danger, #ff4d4f); font-style: normal; }
.form-row input, .form-row select, .form-row textarea { width: 100%; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

.preview-line {
  background: rgba(255, 107, 53, 0.07);
  border-radius: var(--radius-sm);
  padding: 9px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.preview-line b { color: var(--color-primary); font-size: 15px; }

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

@media (max-width: 640px) {
  .form-grid { grid-template-columns: 1fr; }
  .act-grid { grid-template-columns: 1fr; }
}
</style>
