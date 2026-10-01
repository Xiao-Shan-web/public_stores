<template>
  <div class="profile-hub">
    <!-- 顶部头像区 -->
    <div class="profile-header" @click="goProfileEdit">
      <!-- 头像点击放大预览，阻止冒泡避免触发整行跳编辑页 -->
      <div class="avatar" @click.stop>
        <UserAvatar :src="avatar" previewable />
      </div>
      <p class="nickname">{{ displayName }}</p>
      <span class="profile-entry">
        编辑资料 <i class="fas fa-chevron-right"></i>
      </span>
    </div>

    <!-- 子菜单导航 -->
    <nav class="profile-nav card">
      <router-link
        v-for="item in menus"
        :key="item.path"
        :to="item.path"
        class="nav-item"
        active-class="active"
      >
        <i :class="['fas', item.icon]"></i>
        <span>{{ item.label }}</span>
        <i class="fas fa-chevron-right arrow"></i>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import UserAvatar from '@/components/UserAvatar.vue'
import { resolveAvatar } from '@/utils/avatar'
import { userAPI } from '@/api/userAPI'

// keep-alive 缓存按组件 name 匹配
defineOptions({ name: 'UserProfile' })

const router = useRouter()
const authStore = useAuthStore()

// 资料页保存后全局 store 已即时更新；这里再做一次后端同步兜底（刷新/多端场景）
const remoteAvatar = ref('')
const remoteNickname = ref('')

const maskedPhone = computed(() => {
  const p = authStore.userInfo?.phone || ''
  if (p.length === 11) return `${p.slice(0, 3)}****${p.slice(7)}`
  return p || '未登录'
})

// 头像：后端资料 > 全局 store
const avatar = computed(() => resolveAvatar(remoteAvatar.value || authStore.userInfo?.avatar))
// 展示名：昵称 > 脱敏手机号
const displayName = computed(
  () => remoteNickname.value || authStore.userInfo?.nickname || maskedPhone.value
)

const goProfileEdit = () => router.push('/user/profile-edit')

// 每次从编辑页返回（keep-alive 重新激活）时拉取最新资料
// 前置条件用 token（同步可得）而非 isLoggedIn（整页刷新时异步恢复），否则首屏会跳过拉取
onActivated(async () => {
  if (!authStore.token) return
  try {
    const res = await userAPI.getProfile()
    if (res.success && res.data) {
      remoteAvatar.value = res.data.avatar || ''
      remoteNickname.value = res.data.nickname || ''
      authStore.patchUserInfo({
        avatar: remoteAvatar.value || null,
        nickname: remoteNickname.value || null
      })
    }
  } catch {
    /* 静默，沿用 store 中的头像昵称 */
  }
})

// 跳转到独立子页面（depth=1，带返回按钮）
const menus = [
  { path: '/user/profile-edit', label: '个人资料', icon: 'fa-user-edit' },
  { path: '/user/membership', label: '我的会员卡', icon: 'fa-id-card' },
  { path: '/user/coupon-center', label: '领券中心', icon: 'fa-ticket-alt' },
  { path: '/user/activities', label: '限时活动', icon: 'fa-bullhorn' },
  { path: '/user/face-register', label: '录入人脸', icon: 'fa-user-shield' },
  { path: '/user/my-records', label: '核销记录', icon: 'fa-history' },
  { path: '/user/share', label: '我的分享', icon: 'fa-share-alt' },
  { path: '/user/complaints', label: '投诉建议', icon: 'fa-comment-dots' },
  { path: '/user/settings', label: '设置', icon: 'fa-cog' }
]
</script>

<style scoped>
.profile-hub {
  min-height: 100vh;
  background: var(--color-bg, #f5f5f5);
}

.profile-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--spacing-xl) 0 var(--spacing-lg);
  background: var(--gradient-primary);
  color: #fff;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  margin-bottom: var(--spacing-md);
  box-shadow: var(--shadow-active);
  cursor: pointer;
}

.avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.6);
  margin-bottom: var(--spacing-sm);
  background: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.9);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nickname {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}

.profile-entry {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.85;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.profile-entry i {
  font-size: 10px;
}

/* 子菜单 */
.profile-nav {
  margin: 0 var(--spacing-md) var(--spacing-md);
  padding: 0 var(--spacing-md);
}

.nav-item {
  display: flex;
  align-items: center;
  padding: var(--spacing-md) 0;
  border-bottom: 1px solid var(--color-border);
  text-decoration: none;
  color: var(--color-text);
  font-size: 15px;
}

.nav-item:last-child {
  border-bottom: none;
}

.nav-item > i:first-child {
  width: 24px;
  color: var(--color-primary);
  margin-right: var(--spacing-md);
  font-size: 16px;
}

.nav-item span {
  flex: 1;
}

.arrow {
  color: var(--color-text-placeholder);
  font-size: 12px;
}

.nav-item.active {
  color: var(--color-primary);
  font-weight: 600;
}

.nav-item.active > i:first-child {
  color: var(--color-primary);
}
</style>
