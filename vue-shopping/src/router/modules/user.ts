export default [

  // ============ 用户端路由 - 移动端布局（全量子路由） ============
  // 底部导航页 depth=1（keep-alive 缓存、显示 tabbar）
  // 独立子页 depth=2（不缓存、隐藏 tabbar、前进/后退滑动动画）
  // 深层详情页 depth=3（OrderDetail/ProductDetail，从子页进入）
  {
    path: '/user',
    name: 'MobileLayout',
    component: () => import('@/views/user/Layout/MobileLayout.vue'),
    redirect: '/user/dashboard',
    meta: { requiresAuth: true },
    children: [
      // ============ 底部导航页（depth 1） ============
      {
        path: 'dashboard',
        name: 'UserDashboard',
        component: () => import('@/views/user/Dashboard.vue'),
        meta: { requiresAuth: true, depth: 1 }
      },
      {
        path: 'messages',
        name: 'UserMessages',
        component: () => import('@/views/user/Messages.vue'),
        meta: { requiresAuth: true, depth: 1 }
      },
      {
        path: 'center',
        name: 'UserCenter',
        component: () => import('@/views/user/Center.vue'),
        meta: { requiresAuth: true, depth: 1 }
      },
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/user/Cart.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 独立子页（depth 2） ============

      // 搜索页
      {
        path: 'search',
        component: () => import('@/views/user/SearchLayout.vue'),
        redirect: '/user/search/default',
        meta: { requiresAuth: true, depth: 2 },
        children: [
          {
            path: 'default',
            name: 'SearchDefault',
            component: () => import('@/views/user/SearchDefault.vue')
          },
          {
            path: 'result',
            name: 'SearchResult',
            component: () => import('@/views/user/SearchResult.vue')
          }
        ]
      },

      // 个人资料
      {
        path: 'profile',
        name: 'UserProfile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 订单列表
      {
        path: 'orders',
        name: 'UserOrders',
        component: () => import('@/views/user/orders/index.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 收藏
      {
        path: 'favorites',
        name: 'UserFavorites',
        component: () => import('@/views/user/Favorites.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 收货地址
      {
        path: 'addresses',
        name: 'UserAddresses',
        component: () => import('@/views/user/Addresses.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 领券中心
      {
        path: 'coupons',
        name: 'UserCoupons',
        component: () => import('@/views/user/Coupons.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 我的优惠券
      {
        path: 'my-coupons',
        name: 'MyCoupons',
        component: () => import('@/views/user/MyCoupons.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // 账户设置
      {
        path: 'setting',
        name: 'UserSetting',
        component: () => import('@/views/user/Setting.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      {
        path: 'merchant/apply',
        name: 'MerchantApply',
        component: () => import('@/views/user/MerchantApply.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 商品相关 ============
      {
        path: 'products/:productId',
        name: 'ProductDetail',
        component: () => import('@/views/user/ProductDetail.vue'),
        props: true,
        meta: { requiresAuth: true, depth: 3 }
      },

      // 商品评论列表
      {
        path: 'products/:productId/reviews',
        name: 'ProductReviews',
        component: () => import('@/views/user/Reviews.vue'),
        props: true,
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 商家店铺 ============
      {
        path: 'shop/:sellerId',
        name: 'Shop',
        component: () => import('@/views/user/Shop.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 结算 ============
      {
        path: 'checkout',
        name: 'Checkout',
        component: () => import('@/views/user/Checkout.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 订单详情 ============
      {
        path: 'orders/:orderId',
        name: 'OrderDetail',
        component: () => import('@/views/user/OrderDetail.vue'),
        props: true,
        meta: { requiresAuth: true, depth: 3 }
      },

      // ============ 评论 ============
      {
        path: 'review/:orderItemId',
        name: 'Review',
        component: () => import('@/views/user/Review.vue'),
        props: true,
        meta: { requiresAuth: true, depth: 2 }
      },
      {
        path: 'reviews',
        name: 'ReviewList',
        component: () => import('@/views/user/ReviewList.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 查询物流 ============
      {
        path: 'logistics',
        name: 'UserLogistics',
        component: () => import('@/views/user/Logistics.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      // ============ 退款售后 ============
      {
        path: 'refund',
        name: 'RefundFlow',
        component: () => import('@/views/user/refund/index.vue'),
        redirect: '/user/refund/apply',
        meta: { requiresAuth: true, depth: 2 },
        children: [
          {
            path: 'apply/:orderItemId',
            name: 'RefundApply',
            component: () => import('@/views/user/refund/components/RefundApply.vue'),
            props: true,
          },
          {
            path: 'chat/:refundId',
            name: 'RefundChatStep',
            component: () => import('@/views/user/refund/components/RefundChatStep.vue'),
            props: true,
          },
          {
            path: 'return/:refundId/:orderItemId',
            name: 'ReturnGoods',
            component: () => import('@/views/user/refund/components/ReturnGoods.vue'),
            props: true,
          },
          {
            path: 'detail/:refundId',
            name: 'RefundDetail',
            component: () => import('@/views/user/RefundDetail.vue'),
            props: true,
          },
        ]
      },

      {
        path: 'after-sale',
        name: 'AfterSaleList',
        component: () => import('@/views/user/AfterSaleList.vue'),
        meta: { requiresAuth: true, depth: 2 }
      },

      {
        path: 'messages/list',
        name: 'MessageList',
        component: () => import('@/components/NotificationList.vue'),
        meta: { title: '通知列表', requiresAuth: true, depth: 2 }
      },
    ]
  },

]
