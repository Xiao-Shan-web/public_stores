<template>
  <div class="identity-center">
    <!-- 导航栏 -->
    <div class="identity-nav">
      <div class="nav-left"></div>
      <div class="nav-center">
        <div class="nav-logo">
          <i class="fas fa-store-alt"></i>
          <span>云杉购</span>
        </div>
        <div class="nav-title">个人中心</div>
      </div>
      <div class="nav-right">
        <div class="nav-icon" @click="goToSetting">
          <i class="fas fa-cog"></i>
        </div>
      </div>
    </div>

    <!-- 骨架屏 -->
    <div v-if="loading" class="skeleton-container">
      <div class="skeleton-card"></div>
      <div class="skeleton-grid">
        <div v-for="i in 4" :key="i" class="skeleton-item"></div>
      </div>
      <div class="skeleton-grid-3">
        <div v-for="i in 6" :key="i" class="skeleton-item"></div>
      </div>
    </div>

    <!-- 真实内容 -->
    <div v-else class="content-wrapper">
      <!-- 用户身份卡片 -->
      <div class="identity-card" @click="router.push({ name: 'UserProfile' })">
        <div class="card-content">
          <div class="avatar-section">
            <div class="avatar-ring">
              <img :src="userProfile.avatar || defaultAvatar" class="avatar-img" />
            </div>
          </div>
          <div class="user-info">
            <h3 class="user-name">{{ userProfile.nickname || '云杉用户' }}</h3>
            <p class="user-since">加入云杉购 · 探索好物</p>
          </div>
          <div class="stats-section">
            <div class="stat-item" @click.stop="goToOrders('all')">
              <span class="stat-value">{{ orderCount }}</span>
              <span class="stat-label">订单</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item" @click.stop="router.push({ name: 'UserFavorites' })">
              <span class="stat-value">{{ favoriteCount }}</span>
              <span class="stat-label">收藏</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-value">{{ couponCount }}</span>
              <span class="stat-label">优惠券</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 我的订单栏 -->
      <div class="section-header" @click="goToOrders('all')">
        <span class="section-title">我的订单</span>
        <span class="section-more">查看全部 <i class="fas fa-chevron-right"></i></span>
      </div>

      <!-- 订单状态胶囊 -->
      <div class="order-status-wrap">
        <div class="status-capsules">
          <div class="status-capsule" @click="goToOrders('all')">
            <div class="capsule-icon all"><i class="fas fa-list"></i></div>
            <span class="capsule-text">全部</span>
          </div>
          <div
            v-for="item in orderStatusItems"
            :key="item.status"
            class="status-capsule"
            @click="goToOrders(item.status)"
          >
            <div class="capsule-icon" :class="getStatusClass(item.status)">
              <i :class="item.icon"></i>
              <span v-if="item.count > 0" class="capsule-count">{{ item.count > 99 ? '99+' : item.count }}</span>
            </div>
            <span class="capsule-text">{{ item.label }}</span>
          </div>
        </div>
      </div>

      <!-- 常用工具标题 -->
      <div class="section-header">
        <span class="section-title">常用工具</span>
      </div>

      <!-- 常用工具网格 -->
      <div class="tool-grid">
        <div class="tool-item" v-for="item in commonTools" :key="item.name" @click="goTo(item.name)">
          <div class="tool-icon-wrapper">
            <div class="tool-icon" :style="{ background: item.gradient }">
              <i :class="item.icon"></i>
            </div>
            <span v-if="item.badge > 0" class="tool-badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
          </div>
          <span class="tool-label">{{ item.label }}</span>
        </div>
      </div>

      <!-- 交易服务标题 -->
      <div class="section-header">
        <span class="section-title">交易服务</span>
      </div>

      <!-- 交易服务网格 -->
      <div class="tool-grid">
        <div class="tool-item" v-for="item in tradeTools" :key="item.name" @click="goTo(item.name)">
          <div class="tool-icon" :style="{ background: item.gradient }">
            <i :class="item.icon"></i>
          </div>
          <span class="tool-label">{{ item.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'UserCenter' })

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import { useAuthStore } from '@/stores/auth'
import defaultAvatar from '@/static/images/user-avatar.jpg'

const authStore = useAuthStore()
const router = useRouter()

// 状态类映射
const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'pending',
    PAID: 'paid',
    SHIPPED: 'shipped',
    COMPLETED: 'completed',
    AFTER_SALE: 'after-sale'
  }
  return map[status] || ''
}

const userProfile = ref<any>({
  avatar: '',
  nickname: ''
})

const loading = ref(true)

const orderCount = ref(0)
const favoriteCount = ref(0)
const couponCount = ref(0)

const afterSaleCount = ref(0)
const pendingReviewCount = ref(0)

const orderStatusItems = ref([
  { status: 'PENDING', label: '待付款', icon: 'fas fa-wallet', count: 0 },
  { status: 'PAID', label: '待发货', icon: 'fas fa-box', count: 0 },
  { status: 'SHIPPED', label: '待收货', icon: 'fas fa-truck', count: 0 },
])

const commonTools = computed(() => [
  { name: 'UserFavorites', label: '我的收藏', icon: 'fas fa-heart', badge: 0, gradient: 'linear-gradient(135deg, #ff6b6b, #ee5a24)' },
  { name: 'UserAddresses', label: '收货地址', icon: 'fas fa-map-marker-alt', badge: 0, gradient: 'linear-gradient(135deg, #4facfe, #00f2fe)' },
  { name: 'MyCoupons', label: '我的优惠券', icon: 'fas fa-tag', badge: couponCount.value, gradient: 'linear-gradient(135deg, #f093fb, #f5576c)' },
  { name: 'ReviewList', label: '评价', icon: 'fas fa-star', badge: pendingReviewCount.value, gradient: 'linear-gradient(135deg, #4facfe, #00f2fe)' },
  { name: 'AfterSaleList', label: '售后', icon: 'fas fa-headset', badge: afterSaleCount.value, gradient: 'linear-gradient(135deg, #fee2e2, #dc2626)' },
])

const tradeTools = computed(() => {
  const items: { name: string; label: string; icon: string; badge: number; gradient: string }[] = [
    { name: '', label: '客服中心', icon: 'fas fa-comments', badge: 0, gradient: 'linear-gradient(135deg, #ff9a9e, #fecfef)' },
  ]
  if (authStore.hasRole('ROLE_SELLER') || authStore.hasRole('ROLE_ADMIN')) {
    items.push({ name: 'SellerDashboard', label: '商家中心', icon: 'fas fa-store', badge: 0, gradient: 'linear-gradient(135deg, #43e97b, #38f9d7)' })
  } else {
    items.push({ name: 'MerchantApply', label: '商家入驻', icon: 'fas fa-store-alt', badge: 0, gradient: 'linear-gradient(135deg, #43e97b, #38f9d7)' })
  }
  return items
})

const goTo = async (name: string) => {
  if (!name) {
    Message.info('该功能正在扩展中，敬请期待...')
    return
  }
  if (name === 'AfterSaleList') {
    router.push({ name: 'AfterSaleList' })
    return
  }
  if (name === 'MerchantApply') {
    router.push({ name: 'MerchantApply' })
    return
  }
  if (name === 'SellerDashboard') {
    const success = await authStore.switchRole('ROLE_SELLER')
    if (success) {
      router.push({ name: 'SellerDashboard' })
    } else {
      Message.error('切换角色失败')
    }
    return
  }
  router.push({ name })
}

const goToOrders = (status: string) => {
  if (status === 'AFTER_SALE') {
    router.push({ name: 'AfterSaleList' })
    return
  }
  router.push({ name: 'UserOrders', query: { status } })
}
const goToSetting = () => router.push({ name: 'UserSetting' })

const loadUserProfile = async () => {
  try {
    const response = await authAPI.getUserProfile()
    if (response.success && response.data?.profile) {
      userProfile.value = {
        avatar: response.data.profile.avatar || '',
        nickname: response.data.profile.nickname || ''
      }
    }
  } catch {}
}

const loadStats = async () => {
  try {
    const response = await authAPI.getOrderCounts()
    if (response.success && response.data?.counts) {
      const counts = response.data.counts
      const { total, ...statusCounts } = counts

      // 待评价数量 = 已完成的订单中未评价的订单项数量
      // 后端接口 GET /api/v1/orders/counts 需要包含 pendingReview 字段
      pendingReviewCount.value = counts.pendingReview || counts.PENDING_REVIEW || 0

      orderStatusItems.value = [
        { status: 'PENDING', label: '待付款', icon: 'fas fa-wallet', count: counts.PENDING || 0 },
        { status: 'PAID', label: '待发货', icon: 'fas fa-box', count: counts.PAID || 0 },
        { status: 'SHIPPED', label: '待收货', icon: 'fas fa-truck', count: counts.SHIPPED || 0 },
      ]
      orderCount.value = total || Object.values(statusCounts).reduce((sum: number, val: any) => sum + (val || 0), 0)
    }
  } catch {}

  try {
    const favResponse = await authAPI.getFavorites({ page: 1, pageSize: 1 })
    if (favResponse.success) {
      const data = favResponse.data
      if (Array.isArray(data)) {
        favoriteCount.value = data.length
      } else if (data) {
        favoriteCount.value = data.total || (data.records ? data.records.length : 0)
      }
    }
  } catch {}
}

const loadAfterSaleCount = async () => {
  try {
    const response = await authAPI.getPendingRefundCount()
    if (response.success && response.data) {
      afterSaleCount.value = response.data.count || 0
    }
  } catch {}
}

onMounted(async () => {
  if (!authStore.validateUserPermission()) return

  loading.value = true
  await Promise.all([
    loadUserProfile(),
    loadStats(),
    loadAfterSaleCount()
  ])
  loading.value = false
})
</script>

<style scoped>
@import url('@/static/css/user/个人中心.css');
</style>
