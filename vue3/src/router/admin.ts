import type { RouteRecordRaw } from 'vue-router'

export default [
  {
    path: '/admin',
    component: () => import('@/views/admin/Layout.vue'),
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '控制台' } },
      { path: 'stats', name: 'AdminStats', component: () => import('@/views/admin/Stats.vue'), meta: { title: '数据统计' } },
      { path: 'members', name: 'AdminMembers', component: () => import('@/views/admin/Members.vue'), meta: { title: '会员管理' } },
      { path: 'records', name: 'AdminRecords', component: () => import('@/views/admin/Records.vue'), meta: { title: '核销记录' } },
      { path: 'entry', name: 'AdminEntry', component: () => import('@/views/admin/Entry.vue'), meta: { title: '到店核销' } },
      { path: 'memberships', name: 'AdminMemberships', component: () => import('@/views/admin/Memberships.vue'), meta: { title: '会员卡管理' } },
      { path: 'cards', name: 'AdminCards', component: () => import('@/views/admin/Cards.vue'), meta: { title: '卡类型管理' } },
      { path: 'coupons', name: 'AdminCoupons', component: () => import('@/views/admin/Coupons.vue'), meta: { title: '优惠券管理' } },
      { path: 'activities', name: 'AdminActivities', component: () => import('@/views/admin/Activities.vue'), meta: { title: '限时活动' } },
      { path: 'stores', name: 'AdminStores', component: () => import('@/views/admin/Stores.vue'), meta: { title: '门店管理' } },
      { path: 'notifications', name: 'AdminNotifications', component: () => import('@/views/admin/Notifications.vue'), meta: { title: '系统通知' } },
      { path: 'service', name: 'AdminService', component: () => import('@/views/admin/Service.vue'), meta: { title: '客服消息' } },
      { path: 'shares', name: 'AdminShares', component: () => import('@/views/admin/Shares.vue'), meta: { title: '分享管理' } },
      { path: 'complaints', name: 'AdminComplaints', component: () => import('@/views/admin/Complaints.vue'), meta: { title: '投诉仲裁' } },
      { path: 'risk', name: 'AdminRisk', component: () => import('@/views/admin/Risk.vue'), meta: { title: '风控中心' } },
      { path: 'audit-logs', name: 'AdminAuditLogs', component: () => import('@/views/admin/AuditLogs.vue'), meta: { title: '操作审计' } },
      { path: 'settings', name: 'AdminSettings', component: () => import('@/views/admin/Settings.vue'), meta: { title: '系统设置' } }
    ]
  }
] as RouteRecordRaw[]
