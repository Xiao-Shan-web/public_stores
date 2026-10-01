<template>
  <div class="audit">
    <div class="filter-bar card">
      <select v-model="filter.module" @change="load(1)">
        <option value="">全部模块</option>
        <option value="members">会员</option>
        <option value="memberships">会员卡</option>
        <option value="cards">卡类型</option>
        <option value="coupons">优惠券</option>
        <option value="activities">限时活动</option>
        <option value="stores">门店</option>
        <option value="complaints">投诉</option>
        <option value="arbitrations">仲裁</option>
        <option value="risk-events">风控</option>
        <option value="notifications">通知</option>
        <option value="shares">分享</option>
        <option value="auth">登录</option>
      </select>
      <select v-model="filter.action" @change="load(1)">
        <option value="">全部动作</option>
        <option value="POST">新增</option>
        <option value="PUT">更新</option>
        <option value="DELETE">删除</option>
        <option value="PATCH">变更</option>
      </select>
      <input v-model="adminIdInput" class="admin-input" placeholder="管理员ID"
             @keyup.enter="applyAdmin" @blur="applyAdmin" />
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>时间</th><th>管理员</th><th>模块</th><th>动作</th><th>接口</th><th>参数</th><th>IP</th><th>结果</th><th>耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in list" :key="l.id">
            <td class="time-cell">{{ fmt(l.createdAt) }}</td>
            <td>
              <div>{{ l.username || '-' }}</div>
              <div class="cell-sub">ID: {{ l.adminId || '-' }}</div>
            </td>
            <td>{{ l.module }}</td>
            <td><span class="action-tag" :class="l.action">{{ actionText(l.action) }}</span></td>
            <td class="uri-cell">{{ l.uri }}</td>
            <td class="param-cell">{{ l.paramSummary || '-' }}</td>
            <td>{{ l.ip || '-' }}</td>
            <td>
              <span class="result-tag" :class="l.result === 'SUCCESS' ? 'tag-success' : 'tag-fail'">
                {{ l.result === 'SUCCESS' ? '成功' : '失败' }}
              </span>
            </td>
            <td>{{ l.costMs }}ms</td>
          </tr>
          <tr v-if="!list.length"><td colspan="9" class="empty-row">暂无审计日志</td></tr>
        </tbody>
      </table>
      <div class="pager">
        <button :disabled="page <= 1" @click="load(page - 1)">上一页</button>
        <span>第 {{ page }} 页 / 共 {{ Math.max(1, Math.ceil(total / 20)) }} 页（{{ total }} 条）</span>
        <button :disabled="page >= Math.ceil(total / 20)" @click="load(page + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { governanceAPI } from '@/api/governanceAPI'
import type { AuditLog } from '@/api/governanceAPI'

const list = ref<AuditLog[]>([])
const page = ref(1); const total = ref(0)
const filter = reactive({ module: '', action: '' })
const adminIdInput = ref('')

const load = async (p = 1) => {
  page.value = p
  const adminId = adminIdInput.value.trim() ? Number(adminIdInput.value.trim()) : undefined
  const res = await governanceAPI.adminAuditLogs({
    ...filter,
    adminId: adminId && !Number.isNaN(adminId) ? adminId : undefined,
    page: p, size: 20
  })
  if (res.success && res.data) { list.value = res.data.list; total.value = res.data.total }
}

const applyAdmin = () => load(1)

const actionText = (a: string) => ({ POST: '新增', PUT: '更新', DELETE: '删除', PATCH: '变更' } as Record<string, string>)[a] || a
const fmt = (t?: string | null) => (t ? t.replace('T', ' ').substring(0, 16) : '-')

onMounted(() => load(1))
</script>

<style scoped>
.filter-bar { display: flex; gap: 10px; padding: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.filter-bar select, .admin-input {
  padding: 7px 10px; border: 1px solid #e5e5e5; border-radius: 6px; font-size: 13px;
}
.admin-input { width: 120px; }
.cell-sub { font-size: 11.5px; color: #999; }
.action-tag { padding: 2px 8px; border-radius: 6px; font-size: 12px; }
.action-tag.POST { background: #f0fff0; color: #52c41a; }
.action-tag.PUT, .action-tag.PATCH { background: #e6f4ff; color: #1677ff; }
.action-tag.DELETE { background: #fff1f0; color: #f5222d; }
.uri-cell { font-size: 12px; color: #555; max-width: 240px; word-break: break-all; }
.param-cell { font-size: 12px; color: #999; max-width: 180px; word-break: break-all; }
.result-tag { padding: 2px 9px; border-radius: 9px; font-size: 12px; }
.tag-success { background: #f0fff0; color: #52c41a; }
.tag-fail { background: #fff1f0; color: #f5222d; }

.pager { display: flex; align-items: center; justify-content: center; gap: 14px; padding: 14px 0 4px; font-size: 13px; color: #888; }
.pager button { border: 1px solid #e5e5e5; background: #fff; border-radius: 6px; padding: 5px 14px; font-size: 13px; cursor: pointer; }
.pager button:disabled { color: #ccc; cursor: not-allowed; }
</style>
