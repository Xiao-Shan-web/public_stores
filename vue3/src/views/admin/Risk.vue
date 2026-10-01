<template>
  <div class="risk">
    <div class="filter-bar card">
      <select v-model="filter.status" @change="load(1)">
        <option value="">全部状态</option>
        <option value="OPEN">待处理</option>
        <option value="HANDLED">已处理</option>
        <option value="IGNORED">已忽略</option>
      </select>
      <select v-model="filter.level" @change="load(1)">
        <option value="">全部等级</option>
        <option value="HIGH">高风险</option>
        <option value="MEDIUM">中风险</option>
        <option value="LOW">低风险</option>
      </select>
      <select v-model="filter.type" @change="load(1)">
        <option value="">全部类型</option>
        <option value="LOGIN_FAIL_BURST">登录失败爆发</option>
        <option value="ENTRY_FREQ">高频核销</option>
        <option value="ENTRY_NIGHT">夜间核销</option>
      </select>
      <span v-if="openCount > 0" class="open-badge">待处理 {{ openCount }} 条</span>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>等级</th><th>类型</th><th>主体</th><th>详情</th><th>时间</th><th>状态</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="loading-row">加载中…</td></tr>
          <tr v-for="e in list" :key="e.id">
            <td><span class="level-tag" :class="e.riskLevel">{{ levelText(e.riskLevel) }}</span></td>
            <td>{{ typeText(e.eventType) }}</td>
            <td>
              <div>{{ e.subjectType === 'ADMIN' ? '管理员' : '用户' }}</div>
              <div class="cell-sub">{{ e.subjectId || '-' }} {{ e.subjectName ? '(' + e.subjectName + ')' : '' }}</div>
            </td>
            <td class="cell-detail">{{ detailText(e.detailJson) }}</td>
            <td class="time-cell">{{ fmt(e.createdAt) }}</td>
            <td>
              <span class="status-tag" :class="e.status">{{ statusText(e.status) }}</span>
              <div v-if="e.handleRemark" class="cell-sub">{{ e.handleRemark }}</div>
            </td>
            <td>
              <template v-if="e.status === 'OPEN'">
                <button class="link-btn" :disabled="handlingIds.has(e.id)" @click="handle(e, 'HANDLED')">
                  {{ handlingIds.has(e.id) ? '处理中…' : '标记处理' }}
                </button>
                <button class="link-btn gray" :disabled="handlingIds.has(e.id)" @click="handle(e, 'IGNORED')">忽略</button>
              </template>
              <span v-else class="cell-sub">{{ fmt(e.handledAt) }}</span>
            </td>
          </tr>
          <tr v-if="!loading && !list.length"><td colspan="7" class="empty-row">暂无风控事件</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <button :disabled="page <= 1" @click="load(page - 1)">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.max(1, Math.ceil(total / 10)) }} 页（{{ total }} 条）</span>
        <button :disabled="page >= Math.ceil(total / 10)" @click="load(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from '@/utils/message'
import { confirmInput } from '@/composables/useConfirm'
import { governanceAPI } from '@/api/governanceAPI'
import type { RiskEvent, RiskStatus } from '@/api/governanceAPI'

const list = ref<RiskEvent[]>([])
const page = ref(1); const total = ref(0); const openCount = ref(0)
const loading = ref(false)
const handlingIds = ref<Set<number>>(new Set())
const filter = reactive({ status: '', level: '', type: '' })

const load = async (p = 1) => {
  page.value = p
  loading.value = true
  try {
    const res = await governanceAPI.adminRiskEvents({ ...filter, page: p, size: 10 })
    if (res.success && res.data) {
      list.value = res.data.list
      total.value = res.data.total
      openCount.value = res.data.openCount || 0
    }
  } finally {
    loading.value = false
  }
}

const handle = async (e: RiskEvent, status: RiskStatus) => {
  const remark = await confirmInput({
    title: status === 'HANDLED' ? '标记已处理' : '忽略事件',
    content: status === 'HANDLED' ? '确认将该事件标记为已处理？' : '确认忽略该事件？',
    inputLabel: '处理备注',
    inputPlaceholder: '选填',
    confirmText: '确认',
    danger: status !== 'HANDLED'
  })
  if (remark === null) return
  handlingIds.value.add(e.id)
  try {
    const res = await governanceAPI.handleRiskEvent(e.id, status, remark || undefined)
    if (res.success) {
      message.success('处置完成')
      load(page.value)
    }
  } finally {
    handlingIds.value.delete(e.id)
  }
}

const levelText = (l: string) => ({ HIGH: '高', MEDIUM: '中', LOW: '低' } as Record<string, string>)[l] || l
const statusText = (s: string) => ({ OPEN: '待处理', HANDLED: '已处理', IGNORED: '已忽略' } as Record<string, string>)[s] || s
const typeText = (t: string) => ({
  LOGIN_FAIL_BURST: '登录失败爆发',
  ENTRY_FREQ: '高频核销',
  ENTRY_NIGHT: '夜间核销'
} as Record<string, string>)[t] || t
const fmt = (t?: string | null) => (t ? t.replace('T', ' ').substring(0, 16) : '-')
const detailText = (json?: string | null) => {
  if (!json) return '-'
  try {
    const o = JSON.parse(json)
    return Object.entries(o).map(([k, v]) => `${k}=${v}`).join('，')
  } catch {
    return json
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; padding: 12px; margin-bottom: 12px; align-items: center; flex-wrap: wrap; }
.filter-bar select { padding: 7px 10px; border: 1px solid #e5e5e5; border-radius: 6px; font-size: 13px; }
.open-badge { color: #fa541c; font-size: 13px; font-weight: 600; }

.level-tag { padding: 2px 10px; border-radius: 9px; font-size: 12px; font-weight: 600; }
.level-tag.HIGH { background: #fff1f0; color: #f5222d; }
.level-tag.MEDIUM { background: #fff3e0; color: #fa8c16; }
.level-tag.LOW { background: #f0f5ff; color: #2f54eb; }
.status-tag { padding: 2px 9px; border-radius: 9px; font-size: 12px; background: #f0f0f0; color: #888; }
.status-tag.OPEN { background: #fff1f0; color: #f5222d; }
.status-tag.HANDLED { background: #f0fff0; color: #52c41a; }
.cell-sub { font-size: 11.5px; color: #999; margin-top: 2px; }
.cell-detail { font-size: 12px; color: #666; max-width: 280px; }
.link-btn { border: none; background: none; color: var(--color-primary, #ff6a00); font-size: 13px; cursor: pointer; margin-right: 8px; }
.link-btn.gray { color: #999; }
.link-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.loading-row, .empty-row { text-align: center; color: #999; padding: 20px 0; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; padding: 14px 0 4px; font-size: 13px; color: #888; }
.pager button { border: 1px solid #e5e5e5; background: #fff; border-radius: 6px; padding: 5px 14px; font-size: 13px; cursor: pointer; }
.pager button:disabled { color: #ccc; cursor: not-allowed; }
</style>
