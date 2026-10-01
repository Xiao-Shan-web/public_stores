<template>
  <div class="order-detail-container page-container">
    <div class="page-navbar">
      <button class="page-nav-back" @click="router.back()">
        <i class="fas fa-chevron-left"></i>
      </button>
      <div class="page-nav-title">
        <span>订单详情</span>
      </div>
      <div class="page-nav-right"></div>
    </div>

    <!-- 加载状态 - 骨架屏 -->
    <div v-if="loading" class="order-detail-content">
      <div class="skeleton-card skeleton-status-bar"></div>
      <div class="skeleton-card">
        <div class="skeleton-line short"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line medium"></div>
      </div>
      <div class="skeleton-card">
        <div class="skeleton-line short"></div>
        <div class="skeleton-line"></div>
        <div class="skeleton-line medium"></div>
      </div>
      <div class="skeleton-card">
        <div class="skeleton-line short"></div>
        <div class="skeleton-item">
          <div class="skeleton-avatar"></div>
          <div class="skeleton-item-content">
            <div class="skeleton-line medium"></div>
            <div class="skeleton-line short"></div>
          </div>
        </div>
        <div class="skeleton-line"></div>
      </div>
    </div>

    <!-- 订单不存在 -->
    <div v-else-if="!orderData" class="empty-cart">
      <i class="fas fa-search"></i>
      <p>订单不存在</p>
      <button class="btn btn-primary" @click="goBack">返回订单列表</button>
    </div>

    <!-- 订单详情内容 -->
    <div v-else class="order-detail-content">
      <!-- 物流轨迹栏 -->
      <div class="logistics-bar" @click="viewLogistics(orderData.id)" v-if="latestTrace">
        <div class="logistics-icon">
          <i class="fas fa-truck"></i>
        </div>
        <div class="logistics-content">
          <div class="trace-text">{{ latestTrace.status || latestTrace.description }}</div>
          <div class="trace-time">{{ formatDateTime(latestTrace.time) }}</div>
        </div>
        <div class="logistics-arrow">
          <i class="fas fa-chevron-right"></i>
        </div>
      </div>

      <!-- 收货地址 -->
      <div class="address-card" v-if="addressData">
        <div class="address-icon">
          <i class="fas fa-map-marker-alt"></i>
        </div>
        <div class="address-content">
          <div class="address-detail">{{ formatAddress(addressData) }}</div>
          <div class="phone-row">
            <span>{{ addressData.recipientName }}</span>
            <span class="phone">{{ showFullPhone ? addressData.recipientPhone : formatPhone(addressData.recipientPhone) }}</span>
            <button class="eye-btn" @click="showFullPhone = !showFullPhone">
              <i :class="showFullPhone ? 'fas fa-eye' : 'fas fa-eye-slash'"></i>
            </button>
            <span class="privacy-tip">保护手机号码</span>
          </div>
        </div>
      </div>
      <div v-else class="address-card empty">
        <div class="address-icon">
          <i class="fas fa-map-marker-alt"></i>
        </div>
        <div class="address-content">
          <div class="address-detail">暂无收货地址</div>
        </div>
      </div>

      <!-- 商品清单 -->
      <div class="info-card">
        <div class="card-title"><i class="fas fa-boxes"></i>商品清单</div>
        <div class="product-list">
          <div v-for="item in orderItems" :key="item.id" class="product-item">
            <img :src="item.productImage || defaultProductImage" class="product-image" @click="viewProduct(item.productId)">
            <div class="product-info">
              <div class="product-name" @click="viewProduct(item.productId)">{{ item.productName }}</div>
              <div class="product-meta">
                <span v-if="item.skuName" class="sku-name">{{ item.skuName }}</span>
                <span class="quantity">x{{ item.quantity }}</span>
              </div>
            </div>
            <div class="product-price">¥{{ formatPrice(item.price) }}</div>
          </div>
        </div>
      </div>

      <!-- 金额汇总 -->
      <div class="amount-card">
        <div class="amount-row">
          <span class="label">商品总额</span>
          <span class="value">¥{{ formatPrice(orderData.totalAmount) }}</span>
        </div>
        <div v-if="couponDiscountAmount > 0" class="amount-row coupon-discount-row">
          <span class="label">优惠券减免</span>
          <span class="value">-¥{{ formatPrice(couponDiscountAmount) }}</span>
        </div>
        <div class="amount-row total">
          <span class="label">实付款</span>
          <span class="value">¥{{ formatPrice(actualPayAmount) }}</span>
        </div>
      </div>

      <!-- 订单信息（折叠） -->
      <div class="info-card collapse-card">
        <div class="collapse-header" @click="orderInfoCollapsed = !orderInfoCollapsed">
          <div class="card-title"><i class="fas fa-file-alt"></i>订单信息</div>
          <i class="fas" :class="orderInfoCollapsed ? 'fa-chevron-down' : 'fa-chevron-up'"></i>
        </div>
        <div class="collapse-content" v-show="!orderInfoCollapsed">
          <div class="info-row">
            <span class="info-label">订单号：</span>
            <span class="info-value">{{ orderData.orderNumber }}</span>
            <button class="copy-btn" @click="copyOrderNumber">
              <i class="fas fa-copy"></i>
            </button>
          </div>
          <div class="info-row">
            <span class="info-label">支付方式：</span>
            <span class="info-value">{{ getPaymentMethodText(orderData.paymentMethod) }}</span>
          </div>
          <div v-if="couponName" class="info-row">
            <span class="info-label">优惠券：</span>
            <span class="info-value coupon-name">{{ couponName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">下单时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.createdAt) }}</span>
          </div>
          <div v-if="orderData.paidAt" class="info-row">
            <span class="info-label">支付时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.paidAt) }}</span>
          </div>
          <div v-if="orderData.shippedAt" class="info-row">
            <span class="info-label">发货时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.shippedAt) }}</span>
          </div>
          <div v-if="orderData.completedAt" class="info-row">
            <span class="info-label">完成时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.completedAt) }}</span>
          </div>
        </div>
      </div>

      <!-- 操作按钮区（申请退款、评价、联系客服按钮） -->
      <div class="action-section">
        <template v-if="orderData?.status === 'PAID' || orderData?.status === 'PROCESSING'">
          <button class="action-btn outline" @click="handleRefund">申请退款</button>
          <button class="action-btn outline" @click="contactService">
            <i class="fas fa-headset"></i>联系客服
          </button>
        </template>
        <template v-else-if="orderData?.status === 'SHIPPED'">
          <button class="action-btn outline" @click="handleAfterSale">申请售后</button>
          <button class="action-btn outline" @click="contactService">
            <i class="fas fa-headset"></i>联系客服
          </button>
        </template>
        <template v-else-if="orderData?.status === 'COMPLETED'">
          <button class="action-btn outline" @click="handleAfterSale">申请售后</button>
          <button class="action-btn outline" @click="goToReview">评价</button>
          <button class="action-btn outline" @click="contactService">
            <i class="fas fa-headset"></i>联系客服
          </button>
        </template>
        <template v-else>
          <button class="action-btn outline" @click="contactService">
            <i class="fas fa-headset"></i>联系客服
          </button>
        </template>
      </div>

      <!-- 底部留空防止被固定操作栏遮挡 -->
      <div class="bottom-space"></div>
    </div>

    <!-- 商品选择弹窗 -->
    <div v-if="showItemSelector" class="item-selector-overlay" @click.self="showItemSelector = false">
      <div class="item-selector-dialog">
        <div class="dialog-header">
          <span>{{ selectorTitle }}</span>
          <button class="dialog-close" @click="showItemSelector = false">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="dialog-content">
          <div
            v-for="item in selectableItems"
            :key="item.id"
            class="selector-item"
            :class="{ selected: selectedItemId === item.id, disabled: !isItemSelectable(item) }"
            @click="isItemSelectable(item) && selectItem(item.id)"
          >
            <img :src="item.productImage" class="selector-image">
            <div class="selector-info">
              <div class="selector-name">{{ item.productName }}</div>
              <div v-if="item.skuName" class="selector-spec">{{ item.skuName }}</div>
              <div class="selector-tags">
                <span v-if="selectorType === 'REVIEW' && item.isReviewed" class="tag status-success">已评价</span>
                <span v-else-if="selectorType !== 'REVIEW' && item.refundStatus === 'SUCCESS'" class="tag status-success">已退款</span>
                <span
                  v-else-if="selectorType !== 'REVIEW' && item.refundStatus === 'RETURNING'"
                  class="tag status-returning clickable"
                  @click="viewReturnLogistics(item)"
                >退货中，查看物流</span>
                <span v-else-if="selectorType !== 'REVIEW' && item.refundStatus === 'PROCESSING'" class="tag status-processing">退款中</span>
                <span v-else-if="selectorType !== 'REVIEW' && item.refundStatus === 'WAITING_RETURN'" class="tag status-waiting-return">待退货</span>
                <span v-else-if="selectorType !== 'REVIEW' && item.refundStatus === 'FAILED'" class="tag status-failed">退款失败</span>
                <span v-else-if="selectorType !== 'REVIEW' && item.refundStatus" class="tag status-default">{{ getRefundStatusText(item.refundStatus, item.refundType, item.returnStatus) }}</span>
              </div>
            </div>
            <div class="selector-radio">
              <i v-if="selectedItemId === item.id" class="fas fa-check-circle"></i>
              <i v-else-if="isItemSelectable(item)" class="far fa-circle"></i>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showItemSelector = false">取消</button>
          <button class="btn-confirm" @click="confirmSelection" :disabled="!selectedItemId">确定</button>
        </div>
      </div>
    </div>

    <!-- 底部固定操作栏（核心操作按钮） -->
    <div v-if="showBottomBar" class="order-footer">
      <div class="order-actions">
        <template v-if="orderData?.status === 'PENDING'">
          <button class="btn-outline btn-danger" @click="cancelOrder">取消订单</button>
          <button class="btn-pay" @click="goToPay">去支付</button>
        </template>
        <template v-if="['SHIPPED'].includes(orderData?.status || '')">
          <button class="btn-outline" @click="viewLogistics(orderData!.id)">查看物流</button>
          <button class="btn-success" @click="confirmReceive">确认收货</button>
        </template>
        <template v-if="['COMPLETED'].includes(orderData?.status || '')">
          <button class="btn-outline" @click="viewLogistics(orderData!.id)">查看物流</button>
        </template>
        <template v-if="['CANCELLED'].includes(orderData?.status || '')">
          <button class="btn-outline btn-danger" @click="deleteOrder">删除订单</button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
  import { useRoute, useRouter } from 'vue-router'
  import { authAPI } from '@/api/authAPI'
  import Message from '@/utils/message'
  import { useAuthStore } from '@/stores/auth'
  import { getRefundStatusText, type OrderStatus } from '@/utils/order'
  import sellerDefaultAvatar from '@/static/images/seller-avatar.jpg'
  import defaultProductImage from '@/static/images/云杉购图标.jpg'


  const authStore = useAuthStore()
  const route = useRoute()
  const router = useRouter()

  // ==================== 类型定义 ====================

  /** 订单状态枚举（含 REFUNDING/REFUNDED，定义见 @/utils/order） */

  /** 支付方式枚举 */
  type PaymentMethod = 'ALIPAY' | 'WECHAT'

  /** 订单来源 */
  type OrderSource = 'cart' | 'product'

  /** 地址实体 */
  interface Address {
    id: number
    userId: number
    recipientName: string
    recipientPhone: string
    province: string
    city: string
    district: string
    detailAddress: string
    label: string
    isDefault: boolean
    createdAt: string
    updatedAt: string
  }

  /** 订单商品项 */
  interface OrderItem {
    id: number
    orderId: number
    productId: number
    sellerId: number
    productName: string
    productImage: string
    quantity: number
    price: number
    totalPrice: number
    skuName?: string
    isReviewed: boolean
    reviewedAt: string | null
    createdAt: string
    refundStatus?: string
    refundType?: string
    returnStatus?: string
    returnTrackingNumber?: string
    returnLogisticsName?: string
  }

  /** 订单实体 */
  interface Order {
    id: number
    orderNumber: string
    userId: number
    totalAmount: number
    status: OrderStatus
    source: OrderSource
    addressId: number
    paymentMethod: PaymentMethod | null
    transactionId: string | null
    paidAt: string | null
    trackingNumber: string | null
    logisticsCode: string | null
    logisticsName: string | null
    shippedAt: string | null
    completedAt: string | null
    createdAt: string
    updatedAt: string
    isDeleted: boolean
    sellerName?: string
    sellerAvatar?: string
    couponId?: number
    userCouponId?: number
    couponName?: string
    couponDiscountAmount?: number
  }

  /** 物流轨迹 */
  interface LogisticsTrace {
    time: string
    description: string
    status: string
    location: string
  }

  /** 物流信息响应 */
  interface LogisticsInfo {
    traces: LogisticsTrace[]
    tracesCopy?: LogisticsTrace[]
  }

  /** 订单详情响应数据（与后端 OrderDetailVO 对应） */
  interface OrderDetailData {
    order: Order
    orderItems: OrderItem[]
    address: Address
    sellerName?: string
    sellerAvatar?: string
    traces?: LogisticsTrace[]
  }

  // ==================== 响应式数据 ====================

  const loading = ref(true)
  const orderData = ref<Order | null>(null)
  const orderItems = ref<OrderItem[]>([])
  const addressData = ref<Address | null>(null)
  const sellerName = ref('')
  const sellerAvatar = ref('')

  // 商品选择弹窗相关
  const showItemSelector = ref(false)
  const selectorTitle = ref('')
  const selectedItemId = ref<number | null>(null)
  const selectorType = ref<'REFUND' | 'AFTER_SALE' | 'REVIEW'>('REFUND')
  const selectableItems = ref<OrderItem[]>([])

  // 物流信息
  const latestTrace = ref<LogisticsTrace | null>(null)

  // 订单信息折叠状态
  const orderInfoCollapsed = ref(true)

  // 手机号显示状态（默认隐匿）
  const showFullPhone = ref(false)

  // ==================== 计算属性 ====================

  const couponDiscountAmount = computed(() => {
    return orderData.value?.couponDiscountAmount || 0
  })

  const couponName = computed(() => {
    return orderData.value?.couponName || ''
  })

  const actualPayAmount = computed(() => {
    const amount = (orderData.value?.totalAmount || 0) - couponDiscountAmount.value
    return Math.max(amount, 0)
  })

  const showBottomBar = computed(() => {
    if (!orderData.value) return false
    const status = orderData.value.status
    return ['PENDING', 'SHIPPED', 'COMPLETED', 'CANCELLED'].includes(status)
  })

  // 是否显示申请售后按钮
  const showAfterSaleBtn = computed(() => {
    if (!orderData.value) return false
    const status = orderData.value.status
    return ['SHIPPED', 'COMPLETED'].includes(status)
  })

  // 是否有活动的退款记录
  const hasActiveRefund = computed(() => {
    return orderItems.value.some(item => item.refundStatus)
  })

  // 是否可以再次申请退款
  const canReapplyRefund = computed(() => {
    const item = orderItems.value[0]
    if (!item) return false
    return item.refundStatus === 'FAILED'
  })

  // 是否已达最大申请次数上限
  const isRefundReachLimit = computed(() => {
    return false
  })

  // 当前退款状态文本
  const refundStatusText = computed(() => {
    const item = orderItems.value[0]
    if (!item?.refundStatus) return ''
    return getRefundStatusText(item.refundStatus, item.refundType, item.returnStatus)
  })

  // ==================== 静态配置 ====================

  /** 订单状态样式映射 */
  const statusClassMap: Record<OrderStatus, string> = {
    PENDING: 'status-pending',
    PAID: 'status-paid',
    PROCESSING: 'status-paid',
    SHIPPED: 'status-shipped',
    COMPLETED: 'status-completed',
    CANCELLED: 'status-cancelled',
    REFUNDING: 'status-refunding',
    REFUNDED: 'status-refunded'
  }

  /** 订单状态图标映射 */
  const statusIconMap: Record<OrderStatus, string> = {
    PENDING: 'fas fa-clock',
    PAID: 'fas fa-check-circle',
    PROCESSING: 'fas fa-spinner',
    SHIPPED: 'fas fa-truck',
    COMPLETED: 'fas fa-check-double',
    CANCELLED: 'fas fa-times-circle',
    REFUNDING: 'fas fa-rotate-left',
    REFUNDED: 'fas fa-money-bill-wave'
  }

  /** 订单状态文字映射 */
  const statusTextMap: Record<OrderStatus, string> = {
    PENDING: '待付款',
    PAID: '待发货',
    PROCESSING: '待发货',
    SHIPPED: '待收货',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REFUNDING: '退款中',
    REFUNDED: '已退款'
  }

  /** 订单状态描述映射 */
  const statusDescMap: Record<OrderStatus, string> = {
    PENDING: '请尽快完成支付，超时订单将自动取消',
    PAID: '商家正在准备您的订单，请耐心等待',
    PROCESSING: '商家正在准备您的订单，请耐心等待',
    SHIPPED: '商品已发出，请留意物流信息',
    COMPLETED: '订单已完成，感谢您的购买',
    CANCELLED: '订单已取消',
    REFUNDING: '退款申请处理中，请耐心等待',
    REFUNDED: '订单已退款'
  }

  /** 支付方式映射 */
  const paymentMethodMap: Record<PaymentMethod, string> = {
    ALIPAY: '支付宝',
    WECHAT: '微信支付'
  }

  // ==================== 数据加载 ====================

  /**
   * 加载订单详情
   * 数据加载优先级：先获取订单详情，再根据状态获取物流信息
   * @returns {Promise<void>}
   */
  const loadOrderDetail = async (): Promise<void> => {
    loading.value = true

    try {
      const orderId = Number(route.params.orderId)

      if (!orderId) {
        Message.error('订单ID不存在')
        router.push({ name: 'UserOrders' })
        return
      }

      // ===== 1. 先获取订单详情 =====
      const response = await authAPI.getOrderDetail(orderId)

      if (response.success && response.data?.orderDetail) {
        const data = response.data.orderDetail as OrderDetailData

        // 订单基本信息
        orderData.value = data.order

        // 订单商品列表（计算小计金额）
        orderItems.value = data.orderItems.map(item => ({
          ...item,
          totalPrice: item.price * item.quantity
        }))

        // 收货地址
        addressData.value = data.address

        // 商家信息
        sellerName.value = data.sellerName || '商家'
        sellerAvatar.value = data.sellerAvatar || ''

        // ===== 2. 根据订单状态加载物流信息 =====
        const status = orderData.value?.status || ''

        if (status === 'PENDING') {
          // PENDING 状态 → 显示默认文案
          latestTrace.value = {
            status: '订单已创建，等待付款',
            time: orderData.value?.createdAt || '',
            description: '',
            location: ''
          }
        } else {
          // 其他状态 → 调真实物流接口，取第一条
          try {
            const logisticsResponse = await authAPI.getUserLogisticsInfo(orderId)
            if (logisticsResponse.success) {
              const traces = logisticsResponse.data?.traces
                          || logisticsResponse.data?.logistics?.traces
                          || logisticsResponse.data?.result?.traces
                          || logisticsResponse.data?.result?.logistics?.traces
              if (traces && traces.length > 0) {
                latestTrace.value = traces[0]
              } else {
                // 接口返回空轨迹时，使用默认文案
                latestTrace.value = {
                  status: getDefaultTraceStatus(status),
                  time: orderData.value?.shippedAt || orderData.value?.paidAt || orderData.value?.createdAt || '',
                  description: '',
                  location: ''
                }
              }
            } else {
              latestTrace.value = null
            }
          } catch (logisticsError) {
            console.error('加载物流信息失败', logisticsError)
            latestTrace.value = null
          }
        }
      } else {
        Message.error(response.message || '订单不存在')
        router.push({ name: 'UserOrders' })
      }
    } catch (error: any) {
      Message.error(error.message || '加载失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取默认物流节点文案
   * @param {string} status - 订单状态
   * @returns {string}
   */
  const getDefaultTraceStatus = (status: string): string => {
    switch (status) {
      case 'PENDING':
        return '等待买家付款'
      case 'PAID':
        return '订单已支付，等待商家处理'
      case 'PROCESSING':
        return '商家正在备货中'
      case 'CANCELLED':
        return '订单已取消'
      default:
        return '订单已提交'
    }
  }

  // ==================== 订单操作 ====================

  /**
   * 取消订单
   * @returns {Promise<void>}
   */
  const cancelOrder = async (): Promise<void> => {
    try {
      await Message.confirm('确定要取消该订单吗？', '取消订单')

      const response = await authAPI.cancelOrder(orderData.value!.id)
      if (response.success) {
        Message.success('取消成功')
        await loadOrderDetail()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        Message.error(error.message || '取消失败')
      }
    }
  }

  /**
   * 删除订单
   * @returns {Promise<void>}
   */
  const deleteOrder = async (): Promise<void> => {
    try {
      await Message.confirm('确定要删除该订单吗？', '删除订单')

      const response = await authAPI.deleteOrder(orderData.value!.id)
      if (response.success) {
        Message.success('删除成功')
        router.push({ name: 'UserOrders' })
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        Message.error(error.message || '删除失败')
      }
    }
  }

  /**
   * 发起支付
   * @returns {Promise<void>}
   */
  const goToPay = async (): Promise<void> => {
    loading.value = true

    try {
      const response = await authAPI.payOrder(orderData.value!.id)

      if (response.success) {
        const paymentHtml = response.data?.paymentHtml

        const payWindow = window.open('', '_blank')
        if (payWindow) {
          payWindow.document.write(paymentHtml)
          payWindow.document.close()
        }
      } else {
        Message.error(response.message || '支付失败')
      }
    } catch (error: any) {
      Message.error(error.message || '支付失败')
    } finally {
      loading.value = false
    }
  }

  /**
   * 确认收货
   * @returns {Promise<void>}
   */
  const confirmReceive = async (): Promise<void> => {
    try {
      await Message.confirm('请确认已收到商品', '确认收货')

      const response = await authAPI.confirmReceive(orderData.value!.id)
      if (response.success) {
        Message.success('确认成功')
        await loadOrderDetail()
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        Message.error(error.message || '操作失败')
      }
    }
  }

  // ==================== 页面跳转 ====================

  /**
   * 查看物流信息
   * @param {number} orderId - 订单ID
   */
  const viewLogistics = (orderId: number): void => {
    const sellerId = orderItems.value[0]?.sellerId
    router.push({
      name: 'UserLogistics',
      query: {
        orderId: String(orderId),
        sellerId: sellerId ? String(sellerId) : ''
      }
    })
  }

  /**
   * 评价商品
   * @param {number} orderItemId - 订单项ID
   */
  const reviewOrderItem = (orderItemId: number): void => {
    router.push({ name: 'Review', params: { orderItemId } })
  }

  /**
   * 处理申请退款
   */
  const handleRefund = (): void => {
    showItemSelectorModal('申请退款', 'REFUND', orderItems.value)
  }

  /**
   * 处理申请售后
   */
  const handleAfterSale = (): void => {
    showItemSelectorModal('申请售后', 'AFTER_SALE', orderItems.value)
  }

  /**
   * 处理评价
   */
  const goToReview = (): void => {
    // 传入所有商品，弹窗内根据 isReviewed 状态显示标签和禁用
    showItemSelectorModal('选择评价商品', 'REVIEW', orderItems.value)
  }

  /**
   * 显示商品选择弹窗
   * @param {string} title - 弹窗标题
   * @param {'REFUND' | 'AFTER_SALE' | 'REVIEW'} type - 操作类型
   * @param {OrderItem[]} items - 可选商品列表
   */
  const showItemSelectorModal = (title: string, type: 'REFUND' | 'AFTER_SALE' | 'REVIEW', items: OrderItem[]): void => {
    selectorTitle.value = title
    selectorType.value = type
    selectableItems.value = items
    selectedItemId.value = null
    showItemSelector.value = true
  }

  /**
   * 选择商品
   * @param {number} itemId - 商品ID
   */
  const selectItem = (itemId: number): void => {
    selectedItemId.value = itemId
  }

  /**
   * 确认选择
   */
  const confirmSelection = (): void => {
    if (!selectedItemId.value) return

    switch (selectorType.value) {
      case 'REFUND':
        goToRefund(selectedItemId.value)
        break
      case 'AFTER_SALE':
        goToAfterSale(selectedItemId.value)
        break
      case 'REVIEW':
        router.push({ name: 'Review', params: { orderItemId: selectedItemId.value } })
        break
    }

    showItemSelector.value = false
    selectedItemId.value = null
  }

  /**
   * 判断商品是否可选
   * @param {OrderItem} item - 订单项
   * @returns {boolean} 是否可选
   */
  const isItemSelectable = (item: OrderItem): boolean => {
    if (selectorType.value === 'REVIEW') {
      return item.isReviewed === false
    }
    if (selectorType.value === 'REFUND' || selectorType.value === 'AFTER_SALE') {
      if (!item.refundStatus) return true
      if (item.refundStatus === 'SUCCESS') return false
      return true
    }
    return true
  }

  /**
   * 检查申请次数限制（最多3次）
   * @param {number} orderItemId - 订单项ID
   * @returns {Promise<boolean>} 是否可以申请
   */
  const checkRefundCount = async (orderItemId: number): Promise<boolean> => {
    const res = await authAPI.getSellerRefundsByOrderItem(orderItemId)
    if (res.success && res.data) {
      if (res.data.length >= 3) {
        Message.warning('已达最大申请次数')
        return false
      }
    }
    return true
  }

  /**
   * 申请退款
   * @param {number} orderItemId - 订单项ID
   */
  const goToRefund = async (orderItemId: number): Promise<void> => {
    const item = orderItems.value.find(i => i.id === orderItemId)
    if (!item) return

    if (item.refundStatus && item.refundStatus !== 'FAILED') {
      await viewRefundProgress(item)
      return
    }

    const canApply = await checkRefundCount(orderItemId)
    if (!canApply) return

    router.push({ name: 'RefundApply', params: { orderItemId: String(orderItemId) } })
  }

  /**
   * 申请售后
   * @param {number} orderItemId - 订单项ID
   */
  const goToAfterSale = async (orderItemId: number): Promise<void> => {
    const item = orderItems.value.find(i => i.id === orderItemId)
    if (!item) return

    if (item.refundStatus && item.refundStatus !== 'FAILED') {
      await viewRefundProgress(item)
      return
    }

    const canApply = await checkRefundCount(orderItemId)
    if (!canApply) return

    router.push({ name: 'RefundApply', params: { orderItemId: String(orderItemId) } })
  }

  /**
   * 查看退款/售后进度（处理按钮点击）
   */
  const handleViewRefundProgress = (): void => {
    const item = orderItems.value[0]
    if (item) {
      viewRefundProgress(item)
    }
  }

  /**
   * 查看退款/售后进度
   * @param {OrderItem} item - 订单项
   */
  const viewRefundProgress = async (item: OrderItem): Promise<void> => {
    try {
      const response = await authAPI.getRefundByOrderItemId(item.id)

      if (response.success && response.data) {
        const refund = response.data
        // 退货退款 + 待退货 → 直接跳转到退货页（未提交退货物流时）
        if (refund.refundType === 'AFTER_SALE' && refund.refundStatus === 'WAITING_RETURN' && !refund.returnStatus) {
          router.push({ name: 'ReturnGoods', params: { refundId: String(refund.id), orderItemId: String(item.id) } })
        } else {
          router.push({
            name: 'RefundChatStep',
            params: { refundId: String(refund.id) }
          })
        }
      } else {
        Message.error('退款记录不存在')
      }
    } catch (error) {
      console.error('获取退款详情失败', error)
      Message.error('获取退款记录失败')
    }
  }

  /**
   * 获取退款状态显示文字（用户端）
   * 统一使用 @/utils/order 的 getRefundStatusText，含 REFUNDING 退款中等全部映射。
   * @param {string} status - 退款状态
   * @param {string} refundType - 退款类型
   * @param {string} returnStatus - 退货状态
   * @returns {string} 显示文字
   */

  /**
   * 获取退款状态样式类
   * @param {string} status - 退款状态
   * @returns {string} CSS类名
   */
  const getRefundStatusClass = (status: string): string => {
    const map: Record<string, string> = {
      'PROCESSING': 'status-refunding',
      'WAITING_RETURN': 'status-waiting-return',
      'RETURNING': 'status-returning',
      'SUCCESS': 'status-completed',
      'FAILED': 'status-failed'
    }
    return map[status] || ''
  }

  /**
   * 获取退款状态图标
   * @param {string} status - 退款状态
   * @returns {string} FontAwesome图标类名
   */
  const getRefundStatusIcon = (status: string): string => {
    const map: Record<string, string> = {
      'PROCESSING': 'fa-clock',
      'WAITING_RETURN': 'fa-truck',
      'RETURNING': 'fa-truck',
      'SUCCESS': 'fa-check-double',
      'FAILED': 'fa-times-circle'
    }
    return map[status] || 'fa-info-circle'
  }

  /**
   * 跳转商品详情
   * @param {number} productId - 商品ID
   */
  const viewProduct = (productId: number): void => {
    router.push({ name: 'ProductDetail', params: { productId } })
  }


  /**
   * 跳转退货页面
   */
  const goToReturn = async (item: OrderItem): Promise<void> => {
    try {
      const response = await authAPI.getRefundByOrderItemId(item.id)
      if (response.success && response.data) {
        router.push({
          name: 'ReturnGoods',
          params: {
            refundId: String(response.data.id),
            orderItemId: String(item.id)
          }
        })
      }
    } catch {
      Message.error('获取退款记录失败')
    }
  }

  /**
   * 查看退货物流
   */
  const viewReturnLogistics = async (item: OrderItem): Promise<void> => {
    try {
      const response = await authAPI.getRefundByOrderItemId(item.id)
      if (response.success && response.data) {
        router.push({
          name: 'UserLogistics',
          query: {
            refundId: String(response.data.id),
            trackingNumber: item.returnTrackingNumber || '',
            logisticsName: item.returnLogisticsName || ''
          }
        })
      }
    } catch {
      Message.error('获取退款记录失败')
    }
  }

  // ==================== 工具函数 ====================

  /**
   * 获取订单状态样式类
   * @param {OrderStatus} status - 订单状态
   * @returns {string} CSS类名
   */
  const getStatusClass = (status: OrderStatus): string => {
    return statusClassMap[status] || ''
  }

  /**
   * 获取订单状态图标
   * @param {OrderStatus} status - 订单状态
   * @returns {string} 图标类名
   */
  const getStatusIcon = (status: OrderStatus): string => {
    return statusIconMap[status] || 'fas fa-shopping-bag'
  }

  /**
   * 获取订单状态文字
   * @param {OrderStatus} status - 订单状态
   * @returns {string} 中文状态描述
   */
  const getStatusText = (status: OrderStatus): string => {
    return statusTextMap[status] || status
  }

  /**
   * 获取订单状态描述
   * @param {OrderStatus} status - 订单状态
   * @returns {string} 状态说明文案
   */
  const getStatusDesc = (status: OrderStatus): string => {
    return statusDescMap[status] || ''
  }

  /**
   * 获取支付方式文字
   * @param {PaymentMethod | null} method - 支付方式
   * @returns {string} 支付方式名称
   */
  const getPaymentMethodText = (method: PaymentMethod | null): string => {
    if (!method) return '未支付'
    return paymentMethodMap[method] || method
  }

  /**
   * 格式化价格
   * @param {number} price - 原始价格
   * @returns {string} 保留两位小数的价格字符串
   */
  const formatPrice = (price: number): string => {
    if (price == null || isNaN(price)) return '0.00'
    return price.toFixed(2)
  }

  /**
   * 复制订单号
   */
  const copyOrderNumber = async () => {
    const orderNumber = orderData.value?.orderNumber
    if (!orderNumber) return

    try {
      await navigator.clipboard.writeText(orderNumber)
      Message.success('已复制')
    } catch (error) {
      Message.error('复制失败')
    }
  }

  /**
   * 格式化日期时间
   * @param {string | null} dateStr - ISO日期字符串
   * @returns {string} 格式化后的日期时间
   */
  const formatDateTime = (dateStr: string | null): string => {
    if (!dateStr) return '-'
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}`
  }

  /**
   * 格式化完整地址
   * @param {Address} address - 地址对象
   * @returns {string} 完整地址字符串
   */
  const formatAddress = (address: Address): string => {
    const parts = [address.province, address.city, address.district, address.detailAddress]
    return parts.filter(p => p?.trim()).join(' ')
  }

  /**
   * 格式化手机号（中间四位隐藏）
   * @param {string} phone - 手机号
   * @returns {string}
   */
  const formatPhone = (phone: string): string => {
    if (!phone) return ''
    if (phone.length === 11) {
      return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
    }
    return phone
  }

  /**
   * 联系客服
   */
  const contactService = (): void => {
    // 从商品项中获取卖家ID
    const sellerId = orderItems.value[0]?.sellerId
    if (sellerId) {
      router.push({ name: 'Chat', params: { targetId: sellerId } })
    } else {
      Message.error('无法获取商家信息')
    }
  }

  const goBack = (): void => {
    router.push({ name: 'UserOrders' })
  }

  // ==================== 生命周期 ====================

  onMounted(() => {
    if (!authStore.validateUserPermission()) return

    loadOrderDetail()
    window.addEventListener('payment-success', handlePaymentSuccess)
  })

  watch(showItemSelector, (val) => {
    if (val) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
  })

  onUnmounted(() => {
    window.removeEventListener('payment-success', handlePaymentSuccess)
    document.body.style.overflow = ''
  })

  const handlePaymentSuccess = () => {
    loadOrderDetail()
  }
</script>

<style scoped>
@import url('@/static/css/user/订单详情.css');
</style>
