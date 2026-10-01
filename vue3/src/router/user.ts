import type { RouteRecordRaw } from 'vue-router'

export default [
  {
    path: '/user',
    component: () => import('@/views/user/Layout.vue'),
    children: [
      { path: '', redirect: '/user/home' },
      // ============ 底部导航页（depth 0，keep-alive 缓存） ============
      {
        path: 'home',
        name: 'UserHome',
        component: () => import('@/views/user/Home.vue'),
        meta: { title: '首页', depth: 0, requiresAuth: true }
      },
      {
        path: 'ai-plan',
        name: 'UserAiPlan',
        component: () => import('@/views/user/AiPlan.vue'),
        meta: { title: 'AI饮食计划', depth: 0, requiresAuth: true }
      },
      {
        path: 'messages',
        name: 'UserMessages',
        component: () => import('@/views/user/Messages.vue'),
        meta: { title: '消息', depth: 0, requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'UserProfile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { title: '我的', depth: 0, requiresAuth: true }
      },

      // ============ 从"我的"跳出的独立页面（depth 1，无底部导航，不缓存） ============
      {
        path: 'membership',
        name: 'UserMembership',
        component: () => import('@/views/user/Card.vue'),
        meta: { title: '我的会员卡', depth: 1, requiresAuth: true }
      },
      {
        path: 'buy-cards',
        name: 'UserBuyCards',
        component: () => import('@/views/user/BuyCards.vue'),
        meta: { title: '办理会员卡', depth: 2, requiresAuth: true }
      },
      {
        path: 'my-records',
        name: 'UserMyRecords',
        component: () => import('@/views/user/Records.vue'),
        meta: { title: '核销记录', depth: 1, requiresAuth: true }
      },
      {
        path: 'face-register',
        name: 'UserFaceRegister',
        component: () => import('@/views/user/FaceRegister.vue'),
        meta: { title: '人脸录入', depth: 2, requiresAuth: true }
      },
      {
        path: 'pay-result',
        name: 'UserPayResult',
        component: () => import('@/views/user/PayResult.vue'),
        meta: { title: '支付结果', depth: 2, requiresAuth: true }
      },
      {
        path: 'service',
        name: 'UserServiceChat',
        component: () => import('@/views/user/ServiceChat.vue'),
        meta: { title: '在线客服', depth: 1, requiresAuth: true }
      },
      {
        path: 'ai-assistant',
        name: 'UserAiAssistant',
        component: () => import('@/views/user/AiAssistant.vue'),
        meta: { title: 'AI 助手', depth: 1, requiresAuth: true }
      },
      {
        path: 'complaints',
        name: 'UserComplaints',
        component: () => import('@/views/user/Complaints.vue'),
        meta: { title: '投诉建议', depth: 1, requiresAuth: true }
      },
      {
        path: 'notifications',
        name: 'UserNotifications',
        component: () => import('@/views/user/Notifications.vue'),
        meta: { title: '系统通知', depth: 1, requiresAuth: true }
      },
      {
        path: 'share',
        name: 'UserShare',
        component: () => import('@/views/user/Share.vue'),
        meta: { title: '我的分享', depth: 1, requiresAuth: true }
      },
      {
        path: 'coupon-center',
        name: 'UserCouponCenter',
        component: () => import('@/views/user/CouponCenter.vue'),
        meta: { title: '领券中心', depth: 1, requiresAuth: true }
      },
      {
        path: 'activities',
        name: 'UserActivities',
        component: () => import('@/views/user/Activities.vue'),
        meta: { title: '限时活动', depth: 1, requiresAuth: true }
      },
      {
        path: 'settings',
        name: 'UserSettings',
        component: () => import('@/views/user/Settings.vue'),
        meta: { title: '设置', depth: 1, requiresAuth: true }
      },
      {
        path: 'profile-edit',
        name: 'UserProfileEdit',
        component: () => import('@/views/user/ProfileEdit.vue'),
        meta: { title: '个人资料', depth: 1, requiresAuth: true }
      }
    ]
  }
] as RouteRecordRaw[]
