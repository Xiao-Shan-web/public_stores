<template>
  <div class="coupon-page">
    <div class="page-head">
      <h2 class="page-title">优惠券管理</h2>
      <button class="primary-btn" @click="openModal()">
        <i class="fas fa-plus"></i> 新建优惠券
      </button>
    </div>

    <!-- 券模板列表 -->
    <div class="card">
      <div class="chart-head">
        <h3 class="chart-title">券模板</h3>
        <span class="chart-sub">共 {{ templates.length }} 张</span>
      </div>
      <div class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>券名称</th>
              <th>类型</th>
              <th>面额 / 门槛</th>
              <th>发行 / 已领</th>
              <th>已核销</th>
              <th>限领</th>
              <th>有效期</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in mergedList" :key="t.id">
              <td class="name-cell">{{ t.name }}</td>
              <td>
                <span class="tag" :class="t.type === 'DISCOUNT' ? 'tag-green' : (t.type === 'DIRECT' ? 'tag-blue' : 'tag-orange')">
                  {{ t.type === 'DISCOUNT' ? '折扣' : (t.type === 'DIRECT' ? '直减' : '满减') }}
                </span>
              </td>
              <td>
                <span class="amount" v-if="t.type === 'DISCOUNT'">
                  {{ foldText(t.discount) }}
                  <em v-if="Number(t.maxDiscount) > 0" class="cap">最高减¥{{ fmt(t.maxDiscount) }}</em>
                </span>
                <span class="amount" v-else>¥{{ fmt(t.amount) }}</span>
                <span class="threshold">{{ Number(t.threshold) > 0 ? `满${fmt(t.threshold)}可用` : '无门槛' }}</span>
              </td>
              <td>{{ t.issuedCount }} / {{ t.totalCount === 0 ? '不限' : t.totalCount }}</td>
              <td>{{ t.usedCount ?? 0 }}</td>
              <td>{{ t.perUserLimit }} 张</td>
              <td class="time-cell">{{ validText(t) }}</td>
              <td>
                <span class="tag" :class="Number(t.status) === 1 ? 'tag-success' : 'tag-muted'">
                  {{ Number(t.status) === 1 ? '上架' : '下架' }}
                </span>
              </td>
              <td class="op-cell">
                <button class="link-btn" @click="openModal(t)">编辑</button>
                <button class="link-btn" @click="toggle(t)">
                  {{ Number(t.status) === 1 ? '下架' : '上架' }}
                </button>
                <button class="link-btn danger" @click="remove(t)">删除</button>
              </td>
            </tr>
            <tr v-if="!mergedList.length">
              <td colspan="9" class="empty-row">暂无优惠券，点击右上角新建</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 新建 / 编辑弹窗 -->
    <div v-if="showModal" class="modal-mask" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-head">
          <h3>{{ form.id ? '编辑优惠券' : '新建优惠券' }}</h3>
          <button class="modal-close" @click="showModal = false"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-row">
            <label>券名称 <em>*</em></label>
            <input v-model.trim="form.name" maxlength="30" placeholder="如：新人满100减20" />
          </div>
          <div class="form-row">
            <label>券类型 <em>*</em></label>
            <select v-model="form.type">
              <option value="FULL_REDUCE">满减券（订单满门槛可减）</option>
              <option value="DIRECT">直减券（无门槛直接减）</option>
              <option value="DISCOUNT">折扣券（按折扣率打折，可封顶）</option>
            </select>
          </div>
          <div class="form-grid">
            <div class="form-row" v-if="form.type !== 'DISCOUNT'">
              <label>抵扣面额（元）<em>*</em></label>
              <input v-model="form.amount" type="number" min="0.01" step="0.01" placeholder="20" />
            </div>
            <div class="form-row" v-else>
              <label>折扣率 <em>*</em></label>
              <input v-model="form.discount" type="number" min="0.01" max="0.99" step="0.05" placeholder="0.85 表示 85 折" />
            </div>
            <div class="form-row">
              <label>使用门槛（元）</label>
              <input
                v-model="form.threshold"
                type="number"
                min="0"
                step="0.01"
                :disabled="form.type === 'DIRECT'"
                placeholder="0 表示无门槛"
              />
            </div>
          </div>
          <div class="form-row" v-if="form.type === 'DISCOUNT'">
            <label>最高优惠上限（元）</label>
            <input v-model="form.maxDiscount" type="number" min="0" step="0.01" placeholder="留空表示不封顶" />
          </div>
          <div class="form-grid">
            <div class="form-row">
              <label>发行总量</label>
              <input v-model="form.totalCount" type="number" min="0" placeholder="0 表示不限量" />
            </div>
            <div class="form-row">
              <label>每人限领</label>
              <input v-model="form.perUserLimit" type="number" min="1" placeholder="1" />
            </div>
          </div>
          <div class="form-row">
            <label>有效期类型 <em>*</em></label>
            <select v-model="form.validType">
              <option value="DAYS_AFTER_RECEIVE">领取后 N 天内有效</option>
              <option value="FIXED">固定时间区间</option>
            </select>
          </div>
          <div v-if="form.validType === 'DAYS_AFTER_RECEIVE'" class="form-row">
            <label>领取后有效天数 <em>*</em></label>
            <input v-model="form.validDays" type="number" min="1" placeholder="30" />
          </div>
          <div v-else class="form-grid">
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
            <label>状态</label>
            <select v-model="form.status">
              <option :value="1">上架（用户可领）</option>
              <option :value="0">下架</option>
            </select>
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
  adminGetCoupons, adminCreateCoupon, adminUpdateCoupon,
  adminToggleCoupon, adminDeleteCoupon
} from '@/api/operationAPI'
import type { CouponTemplate, CouponStat } from '@/api/operationAPI'

/** 券模板 + 核销统计合并（表格一行一张券） */
interface Row extends CouponTemplate {
  usedCount?: number
}

const templates = ref<CouponTemplate[]>([])
const statMap = ref<Record<number, CouponStat>>({})

const mergedList = computed<Row[]>(() =>
  templates.value.map(t => ({ ...t, usedCount: statMap.value[t.id]?.usedCount ?? 0 }))
)

const loadList = async () => {
  try {
    const res = await adminGetCoupons()
    if (res.success && res.data) {
      templates.value = res.data.templates || []
      const map: Record<number, CouponStat> = {}
      for (const s of res.data.list || []) {
        map[s.couponId] = s
      }
      statMap.value = map
    }
  } catch { /* 拦截器已提示 */ }
}

// ==================== 弹窗表单 ====================
const showModal = ref(false)
const saving = ref(false)

const emptyForm = () => ({
  id: null as number | null,
  name: '',
  type: 'FULL_REDUCE',
  amount: '20',
  discount: '0.85',
  maxDiscount: '',
  threshold: '100',
  totalCount: 1000,
  perUserLimit: 1,
  validType: 'DAYS_AFTER_RECEIVE',
  validDays: 30,
  startTime: '',
  endTime: '',
  status: 1
})

const form = ref(emptyForm())

const openModal = (t?: Row) => {
  if (t) {
    form.value = {
      id: t.id,
      name: t.name,
      type: t.type,
      amount: String(t.amount),
      discount: t.discount != null ? String(t.discount) : '0.85',
      maxDiscount: t.maxDiscount != null ? String(t.maxDiscount) : '',
      threshold: String(t.threshold),
      totalCount: t.totalCount,
      perUserLimit: t.perUserLimit,
      validType: t.validType,
      validDays: t.validDays ?? 30,
      startTime: t.startTime ? t.startTime.substring(0, 16) : '',
      endTime: t.endTime ? t.endTime.substring(0, 16) : '',
      status: t.status
    }
  } else {
    form.value = emptyForm()
  }
  showModal.value = true
}

const save = async () => {
  const f = form.value
  if (!f.name) return message.error('请填写券名称')
  if (f.type === 'DISCOUNT') {
    const rate = Number(f.discount)
    if (!rate || rate <= 0 || rate >= 1) return message.error('折扣率需在 0 与 1 之间（如 0.85 表示 85 折）')
  } else if (!f.amount || Number(f.amount) <= 0) {
    return message.error('抵扣面额必须大于 0')
  }
  if (f.validType === 'DAYS_AFTER_RECEIVE' && (!f.validDays || Number(f.validDays) <= 0)) {
    return message.error('请填写领取后有效天数')
  }
  if (f.validType === 'FIXED' && (!f.startTime || !f.endTime)) {
    return message.error('请填写固定有效期的开始与结束时间')
  }

  const isDiscount = f.type === 'DISCOUNT'
  const payload = {
    name: f.name,
    type: f.type,
    threshold: f.type === 'DIRECT' ? 0 : Number(f.threshold || 0),
    amount: isDiscount ? 0 : Number(f.amount),
    discount: isDiscount ? Number(f.discount) : null,
    maxDiscount: isDiscount && f.maxDiscount !== '' ? Number(f.maxDiscount) : null,
    totalCount: Number(f.totalCount || 0),
    perUserLimit: Number(f.perUserLimit || 1),
    validType: f.validType,
    validDays: f.validType === 'DAYS_AFTER_RECEIVE' ? Number(f.validDays) : null,
    startTime: f.validType === 'FIXED' ? f.startTime : null,
    endTime: f.validType === 'FIXED' ? f.endTime : null,
    status: f.status
  }

  saving.value = true
  try {
    const res = f.id
      ? await adminUpdateCoupon(f.id, payload as any)
      : await adminCreateCoupon(payload as any)
    if (res.success) {
      message.success(f.id ? '修改成功' : '创建成功')
      showModal.value = false
      loadList()
    }
  } catch { /* 拦截器已提示 */ } finally {
    saving.value = false
  }
}

const toggle = async (t: Row) => {
  try {
    const res = await adminToggleCoupon(t.id, Number(t.status) === 1 ? 0 : 1)
    if (res.success) {
      message.success(Number(t.status) === 1 ? '已下架' : '已上架')
      loadList()
    }
  } catch { /* 拦截器已提示 */ }
}

const remove = async (t: Row) => {
  const ok = await confirm({
    title: '删除优惠券',
    content: `确定删除「${t.name}」吗？已发放给用户的券不受影响，但不可再领取。`
  })
  if (!ok) return
  try {
    const res = await adminDeleteCoupon(t.id)
    if (res.success) {
      message.success('已删除')
      loadList()
    }
  } catch { /* 拦截器已提示 */ }
}

// ==================== 展示工具 ====================
const fmt = (v: string | number) => {
  const n = Number(v ?? 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

/** 折扣率文案（0.85 → 8.5折） */
const foldText = (discount?: string | null) => {
  const rate = Number(discount)
  if (!rate || rate <= 0 || rate >= 1) return '折扣'
  return `${(rate * 10).toFixed(1).replace(/\.0$/, '')}折`
}

const validText = (t: CouponTemplate) => {
  if (t.validType === 'FIXED') {
    const s = t.startTime ? t.startTime.substring(0, 10) : '-'
    const e = t.endTime ? t.endTime.substring(0, 10) : '-'
    return `${s} ~ ${e}`
  }
  return `领取后 ${t.validDays ?? 30} 天`
}

onMounted(() => { loadList() })
</script>

<style scoped>
.coupon-page { display: flex; flex-direction: column; gap: var(--spacing-md); }

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

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

.chart-head { display: flex; align-items: baseline; gap: var(--spacing-sm); margin-bottom: var(--spacing-sm); }
.chart-title { font-size: 14px; font-weight: 700; color: var(--color-text); }
.chart-sub { font-size: 12px; color: var(--color-text-placeholder); }

.table-wrap { overflow-x: auto; }

.data-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.data-table th, .data-table td { padding: 12px; text-align: left; border-bottom: 1px solid var(--color-border); }
.data-table th { color: var(--color-text-secondary); font-weight: 600; background: var(--color-bg); white-space: nowrap; }

.name-cell { font-weight: 600; color: var(--color-text); }
.amount { color: var(--color-primary); font-weight: 700; margin-right: 6px; }
.amount .cap { font-size: 12px; font-weight: 500; color: var(--color-text-placeholder); margin-left: 2px; font-style: normal; }
.threshold { font-size: 12px; color: var(--color-text-placeholder); }
.time-cell { font-size: 13px; color: var(--color-text-secondary); white-space: nowrap; }

.op-cell { white-space: nowrap; }
.op-cell .link-btn { color: var(--color-primary); }
.op-cell .link-btn.danger { color: var(--color-danger, #ff4d4f); }

.empty-row { text-align: center; color: var(--color-text-placeholder); padding: 24px 0; }

.tag {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
}
.tag-success { background: rgba(82, 196, 26, 0.12); color: var(--color-success, #52c41a); }
.tag-muted { background: rgba(0, 0, 0, 0.06); color: var(--color-text-secondary); }
.tag-orange { background: rgba(255, 107, 53, 0.12); color: var(--color-primary); }
.tag-blue { background: rgba(24, 144, 255, 0.12); color: #1890ff; }
.tag-green { background: rgba(82, 196, 26, 0.12); color: #52c41a; }

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
  max-width: 560px;
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

.modal-close {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  font-size: 16px;
  cursor: pointer;
  padding: 4px;
}

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
.form-row input, .form-row select { width: 100%; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }

.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

@media (max-width: 640px) {
  .form-grid { grid-template-columns: 1fr; }
}
</style>
