<template>
  <div class="members">
    <div class="toolbar">
      <input v-model="keyword" class="search-input" placeholder="搜索手机号" @keyup.enter="loadList" />
      <button class="btn-search" @click="loadList"><i class="fas fa-search"></i> 查询</button>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>头像</th>
            <th>手机号</th>
            <th>会员卡</th>
            <th>人脸</th>
            <th>注册时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="6" class="empty-row">加载中…</td></tr>
          <tr v-for="m in list" :key="m.id">
            <td>{{ m.id }}</td>
            <td><div class="m-avatar"><UserAvatar :src="resolveAvatar(m.avatarThumb || m.avatar)" :preview-src="resolveAvatar(m.avatar)" previewable /></div></td>
            <td>{{ m.phone }}</td>
            <td>
              <span
                class="card-link"
                :class="cardStatusClass(m.cardStatus)"
                :title="m.cardStatus ? `点击查看该用户会员卡（${m.phone}）` : '无会员卡'"
                @click="goToMemberships(m.phone)"
              >
                {{ cardLabel(m) }}
              </span>
            </td>
            <td>
              <span class="tag" :class="Number(m.faceRegistered) === 1 ? 'tag-active' : 'tag-none'">
                {{ Number(m.faceRegistered) === 1 ? '已录入' : '未录入' }}
              </span>
            </td>
            <td>{{ formatDate(m.createTime) }}</td>
          </tr>
          <tr v-if="!loading && !list.length">
            <td colspan="6" class="empty-row">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '@/api/request'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'

interface AdminMember {
  id: number
  phone: string
  avatar?: string | null
  /** 头像缩略图（列表展示用，后端关联 user_profiles 下发） */
  avatarThumb?: string | null
  cardName: string | null
  cardId: number | null
  remainTimes: number | null
  expireTime: string | null
  cardStatus: string | null
  /** 账号状态：1-正常，0-禁用（TINYINT(1)，后端 CAST 后以 0/1 下发） */
  userStatus: number
  createTime: string
  /** 是否已录入人脸：1-已录入，0-未录入 */
  faceRegistered: number
}

const router = useRouter()
const keyword = ref('')
const list = ref<AdminMember[]>([])
const loading = ref(false)

const loadList = async () => {
  loading.value = true
  try {
    const res = await request<{ list: AdminMember[] }>({
      method: 'get',
      url: '/admin/members',
      params: { keyword: keyword.value }
    })
    if (res.success) list.value = res.data.list || []
  } catch (e) {
    /* 后端接口未就绪时静默 */
  } finally {
    loading.value = false
  }
}

/**
 * 简化卡状态显示：仅区分"有卡(卡名称)/未激活/已过期/已停用/无"，不暴露枚举。
 * - ACTIVE：显示当前有效卡名称
 * - 其他状态：友好文案
 * - 无卡：显示"无"
 */
const cardLabel = (m: AdminMember): string => {
  if (!m.cardStatus) return '无'
  switch (m.cardStatus) {
    case 'ACTIVE': return m.cardName || '有效卡'
    case 'EXPIRED': return '已过期'
    case 'UNACTIVATED': return '未激活'
    case 'DISABLED': return '已停用'
    default: return m.cardName || '有卡'
  }
}

const cardStatusClass = (s: string | null): string => {
  if (!s) return 'card-none'
  switch (s) {
    case 'ACTIVE': return 'card-active'
    case 'EXPIRED': return 'card-expired'
    case 'UNACTIVATED': return 'card-unactivated'
    case 'DISABLED': return 'card-disabled'
    default: return 'card-active'
  }
}

/** 点击会员卡 → 跳转会员卡管理，按手机号筛选 */
const goToMemberships = (phone: string) => {
  router.push({ name: 'AdminMemberships', query: { phone } })
}

const formatDate = (t: string) => {
  return t.replace('T', ' ').substring(0, 10)
}

onMounted(loadList)
</script>

<style scoped>
.members {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.toolbar {
  display: flex;
  gap: var(--spacing-sm);
}

.search-input {
  flex: 1;
  max-width: 240px;
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
}

.search-input:focus {
  border-color: var(--color-primary);
}

.btn-search {
  padding: 8px 16px;
  background-color: var(--color-primary);
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

.m-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--color-bg-gray);
  color: var(--color-text-placeholder);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 20px 0;
}

.tag {
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-none {
  background-color: rgba(144, 147, 153, 0.1);
  color: var(--color-text-placeholder);
}

.tag-active {
  background-color: rgba(82, 196, 26, 0.15);
  color: var(--color-success);
}

/* 会员卡列：可点击跳转 */
.card-link {
  cursor: pointer;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 13px;
  white-space: nowrap;
  display: inline-block;
  transition: background-color 0.15s;
}

.card-link:hover {
  background-color: var(--color-bg-gray);
}

.card-none {
  color: var(--color-text-placeholder);
}

.card-active {
  color: var(--color-success);
  font-weight: 600;
}

.card-expired,
.card-disabled {
  color: var(--color-danger);
}

.card-unactivated {
  color: #ff9e00;
}
</style>
