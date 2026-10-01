<template>
  <div class="records-page page-container">
    <PageHeader title="核销记录" />
    <div class="page-body">
      <div v-if="loading" class="loading-state">
        <i class="fas fa-spinner fa-spin"></i>
        <p>加载中…</p>
      </div>
      <div v-else-if="records.length" class="record-list">
        <div class="record-item card" v-for="item in records" :key="item.id">
          <div class="record-main">
            <p class="record-card">{{ item.cardName }}</p>
            <p class="record-result" :class="item.result === 'SUCCESS' ? 'is-success' : 'is-fail'">
              {{ item.result === 'SUCCESS' ? '入场成功' : '入场失败' }}
            </p>
            <p v-if="item.result === 'FAILED' && item.failReason" class="record-reason">
              原因：{{ item.failReason }}
            </p>
          </div>
          <div class="record-meta">
            <p class="record-time">{{ formatTime(item.useTime) }}</p>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <i class="fas fa-history"></i>
        <p>暂无核销记录</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userAPI, type EntryRecord } from '@/api/userAPI'
import PageHeader from '@/components/PageHeader.vue'

const records = ref<EntryRecord[]>([])
const loading = ref(false)

// 列表展示到分钟：2026-09-09 09:56（后端返回 yyyy-MM-dd HH:mm:ss，不带 T）
const formatTime = (t: string) => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await userAPI.getRecords({ page: 1, size: 50 })
    if (res.success) records.value = res.data.list || []
  } catch (e) {
    /* 静默 */
  } finally {
    loading.value = false
  }
}

onMounted(loadRecords)
</script>

<style scoped>
.records-page {
  display: flex;
  flex-direction: column;
}

.page-body {
    padding: var(--spacing-md);
  }

  .loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 48px 0;
    color: var(--color-text-placeholder);
  }

  .loading-state i {
    font-size: 28px;
  }

  .loading-state p {
    font-size: 14px;
  }

  .record-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.record-main {
  flex: 1;
}

.record-card {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.record-result {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
}

.record-result.is-success {
  color: var(--color-success);
}

.record-result.is-fail {
  color: var(--color-danger);
}

.record-reason {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-placeholder);
}

.record-meta {
  text-align: right;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.record-time {
  color: var(--color-primary);
  font-weight: 500;
  white-space: nowrap;
}
</style>
