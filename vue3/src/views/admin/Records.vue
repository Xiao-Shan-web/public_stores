<template>
  <div class="records">
    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>会员手机号</th>
            <th>会员卡号</th>
            <th>卡名称</th>
            <th>核销时间</th>
            <th>核销结果</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="5" class="empty-row">加载中…</td></tr>
          <tr v-for="r in list" :key="r.id">
            <td>{{ r.phone || '未识别会员' }}</td>
            <td>{{ r.cardNo || '-' }}</td>
            <td>{{ r.cardName || '-' }}</td>
            <td class="time-cell">{{ formatTime(r.useTime) }}</td>
            <td>
              <span class="result-tag" :class="r.result === 'SUCCESS' ? 'tag-success' : 'tag-fail'">
                {{ r.result === 'SUCCESS' ? '入场成功' : '入场失败' }}
              </span>
              <span v-if="r.result === 'FAILED' && r.failReason" class="fail-reason">{{ r.failReason }}</span>
            </td>
          </tr>
          <tr v-if="!loading && !list.length">
            <td colspan="5" class="empty-row">暂无核销记录</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { request } from '@/api/request'

interface AdminRecord {
  id: number
  phone: string | null
  cardNo: string | null
  cardName: string | null
  /** 核销时间（yyyy-MM-dd HH:mm:ss，不带 T） */
  useTime: string
  result: 'SUCCESS' | 'FAILED'
  failReason?: string | null
}

const list = ref<AdminRecord[]>([])
const loading = ref(false)

// 列表展示到分钟：2026-09-09 09:56
const formatTime = (t: string) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

const loadList = async () => {
  loading.value = true
  try {
    const res = await request<{ list: AdminRecord[] }>({
      method: 'get',
      url: '/admin/records'
    })
    if (res.success) list.value = res.data.list || []
  } catch (e) {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

onMounted(loadList)
</script>

<style scoped>
.records {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
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

.time-cell {
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.result-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-success {
  background-color: rgba(82, 196, 26, 0.12);
  color: var(--color-success);
}

.tag-fail {
  background-color: rgba(255, 77, 79, 0.12);
  color: var(--color-danger);
}

.fail-reason {
  margin-left: 6px;
  font-size: 13px;
  color: var(--color-danger);
}

.empty-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 20px 0;
}
</style>
