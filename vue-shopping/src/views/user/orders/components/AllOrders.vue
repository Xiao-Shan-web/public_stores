<template>
  <div class="order-list">
    <!-- 骨架屏 -->
    <div v-if="loading && orders.length === 0">
      <div v-for="i in 3" :key="i" class="skeleton-card">
        <div class="skeleton-line short"></div>
        <div class="skeleton-line medium" style="margin-top: 12px;"></div>
        <div class="skeleton-line" style="margin-top: 8px;"></div>
        <div class="skeleton-line short" style="margin-top: 12px;"></div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="orders.length === 0" class="empty-cart">
      <i class="fas fa-shopping-bag"></i>
      <p>暂无订单</p>
      <button class="btn-primary" @click="goShopping">去逛逛</button>
    </div>

    <!-- 订单列表 -->
    <div v-else>
      <div v-for="orderWrapper in orders" :key="orderWrapper.order.id" class="order-card">
        <!-- 订单头部 -->
        <div class="order-header">
          <div class="order-header-left" @click.stop="goToShop(orderWrapper)">
            <img :src="getSellerAvatar(orderWrapper)" class="seller-avatar" />
            <span class="seller-name">{{ getSellerName(orderWrapper) }}</span>
          </div>
          <span class="order-status" :class="getStatusClass(orderWrapper.order.status)">
            {{ getStatusText(orderWrapper.order.status) }}
          </span>
        </div>

        <!-- 订单商品列表 -->
        <div class="order-items">
          <div
            v-for="item in orderWrapper.orderItems"
            :key="item.id"
            class="order-item"
            @click="viewOrderDetail(orderWrapper.order.id)"
          >
            <img :src="item.productImage" class="item-image">
            <div class="item-info">
              <div class="item-name">{{ item.productName }}</div>
              <div class="tags-group">
                <span v-if="item.skuName" class="tag-spec">{{ item.skuName }}</span>
                <span class="tag-quantity">x{{ item.quantity }}</span>
              </div>
            </div>
            <div class="item-price">¥{{ formatPrice(item.price) }}</div>
          </div>
        </div>

        <!-- 订单底部 -->
        <div class="order-footer" @click.stop>
          <div class="order-actions">
            <!-- 待付款状态：查看详情 + 取消订单 + 去支付 -->
            <template v-if="orderWrapper.order.status === 'PENDING'">
              <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
              <button class="btn-outline btn-danger" @click="cancelOrder(orderWrapper.order.id)">取消订单</button>
              <button class="btn-pay" @click="goToPay(orderWrapper.order.id)">去支付</button>
            </template>

            <!-- 已付款/待发货状态：查看详情 + 申请退款 -->
            <template v-if="orderWrapper.order.status === 'PAID' || orderWrapper.order.status === 'PROCESSING'">
              <template v-if="orderWrapper.orderItems.length === 1">
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
                <template v-if="orderWrapper.orderItems[0]!.refundStatus && orderWrapper.orderItems[0]!.refundStatus !== 'SUCCESS'">
                  <button
                    v-if="orderWrapper.orderItems[0]!.refundStatus === 'FAILED'"
                    class="btn-outline"
                    @click="goToRefund(orderWrapper)"
                  >再次申请</button>
                  <button
                    v-else
                    class="btn-outline"
                    @click="goToRefund(orderWrapper)"
                  >查看售后</button>
                </template>
                <button
                  v-else
                  :class="canRefund(orderWrapper.order) ? 'btn-outline' : 'btn-outline btn-expired'"
                  :disabled="!canRefund(orderWrapper.order)"
                  @click="canRefund(orderWrapper.order) && goToRefund(orderWrapper)"
                >{{ canRefund(orderWrapper.order) ? '申请退款' : '已过权益期' }}</button>
              </template>
              <template v-else>
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
              </template>
            </template>

            <!-- 已发货状态：查看详情 + 查看物流 + 确认收货 + 更多 -->
            <template v-if="orderWrapper.order.status === 'SHIPPED'">
              <template v-if="orderWrapper.orderItems.length === 1">
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
                <button
                  v-if="orderWrapper.order.trackingNumber"
                  class="btn-outline"
                  @click="viewLogistics(orderWrapper.order.id)"
                >查看物流</button>
                <button class="btn-success" @click="confirmReceive(orderWrapper.order.id)">确认收货</button>
                <button class="btn-more" @click="toggleMoreActions(orderWrapper.order.id)">
                    <i class="fas fa-ellipsis-h"></i>
                </button>
                <div class="more-actions-dropdown" v-if="showMoreActions[orderWrapper.order.id]">
                    <template v-if="orderWrapper.orderItems[0]!.refundStatus && orderWrapper.orderItems[0]!.refundStatus !== 'SUCCESS'">
                      <button
                        v-if="orderWrapper.orderItems[0]!.refundStatus === 'FAILED'"
                        class="dropdown-item"
                        @click="handleAfterSaleAction(orderWrapper)"
                      >再次申请</button>
                      <button
                        v-else
                        class="dropdown-item"
                        @click="handleAfterSaleAction(orderWrapper)"
                      >{{ getRefundStatusText(orderWrapper.orderItems[0]!.refundStatus) }}</button>
                    </template>
                    <button
                      v-else
                      class="dropdown-item"
                      :class="{ disabled: !canAfterSale(orderWrapper.order) }"
                      :disabled="!canAfterSale(orderWrapper.order)"
                      @click="canAfterSale(orderWrapper.order) && handleAfterSaleAction(orderWrapper)"
                    >{{ canAfterSale(orderWrapper.order) ? '申请售后' : '已过权益期' }}</button>
                  </div>
              </template>
              <template v-else>
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
                <button
                  v-if="orderWrapper.order.trackingNumber"
                  class="btn-outline"
                  @click="viewLogistics(orderWrapper.order.id)"
                >查看物流</button>
                <button class="btn-success" @click="confirmReceive(orderWrapper.order.id)">确认收货</button>
              </template>
            </template>

            <!-- 已完成状态：查看详情 + 查看物流 + 评价 + 更多 -->
            <template v-if="orderWrapper.order.status === 'COMPLETED'">
              <template v-if="orderWrapper.orderItems.length === 1">
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
                <button
                  v-if="orderWrapper.order.trackingNumber"
                  class="btn-outline"
                  @click="viewLogistics(orderWrapper.order.id)"
                >查看物流</button>
                <button
                  v-if="orderWrapper.orderItems[0]!.isReviewed"
                  class="btn-outline"
                  disabled
                >已评价</button>
                <button
                  v-else
                  class="btn-outline"
                  @click="reviewOrder(orderWrapper.orderItems[0]!.id)"
                >评价</button>
                <button class="btn-more" @click="toggleMoreActions(orderWrapper.order.id)">
                  <i class="fas fa-ellipsis-h"></i>
                </button>
                <div class="more-actions-dropdown" v-if="showMoreActions[orderWrapper.order.id]">
                    <template v-if="orderWrapper.orderItems[0]!.refundStatus && orderWrapper.orderItems[0]!.refundStatus !== 'SUCCESS'">
                      <button
                        v-if="orderWrapper.orderItems[0]!.refundStatus === 'FAILED'"
                        class="dropdown-item"
                        @click="handleAfterSaleAction(orderWrapper)"
                      >再次申请</button>
                      <button
                        v-else
                        class="dropdown-item"
                        @click="handleAfterSaleAction(orderWrapper)"
                      >{{ getRefundStatusText(orderWrapper.orderItems[0]!.refundStatus) }}</button>
                    </template>
                    <button
                      v-else
                      class="dropdown-item"
                      :class="{ disabled: !canAfterSale(orderWrapper.order) }"
                      :disabled="!canAfterSale(orderWrapper.order)"
                      @click="canAfterSale(orderWrapper.order) && handleAfterSaleAction(orderWrapper)"
                    >{{ canAfterSale(orderWrapper.order) ? '申请售后' : '已过权益期' }}</button>
                  </div>
              </template>
              <template v-else>
                <button class="btn-outline" @click="viewOrderDetail(orderWrapper.order.id)">查看详情</button>
                <button
                  v-if="orderWrapper.order.trackingNumber"
                  class="btn-outline"
                  @click="viewLogistics(orderWrapper.order.id)"
                >查看物流</button>
              </template>
            </template>

            <!-- 已取消状态 -->
            <template v-if="orderWrapper.order.status === 'CANCELLED'">
              <button class="btn-outline btn-danger" @click="deleteOrder(orderWrapper.order.id)">删除订单</button>
            </template>
          </div>
        </div>
      </div>

      <!-- 加载状态区域 -->
      <div class="load-more-wrapper">
        <div v-if="loadingMore" class="loading-more-bar">
          <i class="fas fa-spinner fa-spin"></i>
          <span>加载中...</span>
        </div>
        <div v-else-if="!hasMore && orders.length > 0" class="no-more-bar">
          — 已经到底了 —
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated, onDeactivated, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import { useAuthStore } from '@/stores/auth'
import { getRefundStatusText, type OrderStatus } from '@/utils/order'
import sellerDefaultAvatar from '@/static/images/seller-avatar.jpg'

// 事件定义
const emit = defineEmits(['update-tab-counts'])

const router = useRouter()
const authStore = useAuthStore()

// 滚动位置保存
let savedScrollTop = 0

// ==================== 类型定义 ====================

interface OrderItem {
  id: number
  orderId: number
  productId: number
  sellerId: number
  productName: string
  productImage: string
  skuId?: number
  skuName?: string
  quantity: number
  price: number
  totalPrice: number
  isReviewed: boolean
  reviewedAt: string
  createdAt: string
  refundStatus?: string
  sellerName?: string
  sellerAvatar?: string
}

// OrderStatus 类型（含 REFUNDING/REFUNDED）统一使用 @/utils/order 公共定义

type PaymentMethod = 'ALIPAY' | 'WECHAT'

interface Order {
  id: number
  orderNumber: string
  userId: number
  totalAmount: number
  status: OrderStatus
  source: string
  addressId: number
  paymentMethod?: PaymentMethod
  transactionId?: string
  paidAt?: string
  trackingNumber?: string
  logisticsCode?: string
  logisticsName?: string
  shippedAt?: string
  completedAt?: string
  createdAt: string
  updatedAt: string
  isDeleted: boolean
  orderItems: OrderItem[]
}

interface OrderWithItems {
  order: Order
  orderItems: OrderItem[]
}

// ==================== 常量定义 ====================

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

// ==================== 响应式数据 ====================

const orders = ref<OrderWithItems[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const hasMore = ref(true)
const loadingMore = ref(false)
const showMoreActions = ref<Record<number, boolean>>({})

// ==================== 数据加载 ====================

const loadOrders = async (): Promise<void> => {
  if (loading.value) return

  loading.value = true

  try {
    const params: { page: number; pageSize: number } = {
      page: page.value,
      pageSize: pageSize.value
    }

    const response = await authAPI.getOrders(params)

    if (response.success) {
      const data = response.data || {}
      const records = data.records || []

      if (page.value === 1) {
        orders.value = records
      } else {
        orders.value.push(...records)
      }

      hasMore.value = records.length >= pageSize.value

      // 发送标签页数量更新事件
      const counts = {
        all: data.counts?.total || 0,
        PENDING: data.counts?.PENDING || 0,
        PAID: data.counts?.PAID || 0,
        SHIPPED: data.counts?.SHIPPED || 0,
        COMPLETED: data.counts?.COMPLETED || 0
      }
      emit('update-tab-counts', counts)
    }
  } catch (error: any) {
    Message.error(error.message || '加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const toggleMoreActions = (orderId: number) => {
  showMoreActions.value[orderId] = !showMoreActions.value[orderId]
}

// ==================== 订单操作 ====================

const cancelOrder = async (orderId: number): Promise<void> => {
  try {
    await Message.confirm('确定要取消该订单吗？', '取消订单')

    const response = await authAPI.cancelOrder(orderId)
    if (response.success) {
      Message.success('取消成功')
      await loadOrders()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      Message.error(error.message || '取消失败')
    }
  }
}

const deleteOrder = async (orderId: number): Promise<void> => {
  try {
    await Message.confirm('确定要删除该订单吗？', '删除订单')

    const response = await authAPI.deleteOrder(orderId)
    if (response.success) {
      Message.success('删除成功')
      await loadOrders()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      Message.error(error.message || '删除失败')
    }
  }
}

const goToPay = async (orderId: number): Promise<void> => {
  loading.value = true

  try {
    const response = await authAPI.payOrder(orderId)

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

const confirmReceive = async (orderId: number): Promise<void> => {
  try {
    await Message.confirm('请确认已收到商品', '确认收货')

    const response = await authAPI.confirmReceive(orderId)
    if (response.success) {
      Message.success('确认成功')
      await loadOrders()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      Message.error(error.message || '操作失败')
    }
  }
}

// ==================== 页面跳转 ====================

const viewLogistics = (orderId: number): void => {
  router.push({
    name: 'UserLogistics',
    query: { orderId: String(orderId) }
  })
}

const reviewOrder = (orderItemId: number): void => {
  router.push({
    name: 'Review',
    params: { orderItemId }
  })
}

const goToShop = (orderWrapper: OrderWithItems): void => {
  const sellerId = orderWrapper.orderItems[0]?.sellerId
  if (sellerId) {
    router.push({ name: 'Shop', params: { sellerId } })
  }
}

const viewOrderDetail = (orderId: number): void => {
  router.push({
    name: 'OrderDetail',
    params: { orderId }
  })
}

const viewProduct = (productId: number): void => {
  router.push({
    name: 'ProductDetail',
    params: { productId }
  })
}

const goShopping = (): void => {
  router.push({ name: 'UserDashboard' })
}

// ==================== 工具函数 ====================

const canRefund = (order: Order): boolean => {
  if (!order.paidAt) return false
  const paidTime = new Date(order.paidAt).getTime()
  const now = Date.now()
  const days = (now - paidTime) / (1000 * 60 * 60 * 24)
  return days <= 7
}

const canAfterSale = (order: Order): boolean => {
  if (order.status === 'COMPLETED') {
    if (!order.completedAt) return false
    const completedTime = new Date(order.completedAt).getTime()
    const now = Date.now()
    const days = (now - completedTime) / (1000 * 60 * 60 * 24)
    return days <= 7
  }
  const baseTime = order.shippedAt
  if (!baseTime) return false
  const baseTimestamp = new Date(baseTime).getTime()
  const now = Date.now()
  const days = (now - baseTimestamp) / (1000 * 60 * 60 * 24)
  return days <= 15
}

const checkRefundCount = async (orderItemId: number): Promise<boolean> => {
  try {
    const res = await authAPI.getSellerRefundsByOrderItem(orderItemId)
    if (res.success && res.data) {
      if (res.data.length >= 3) {
        Message.warning('已超过申请上限（3次），无法再次申请')
        return false
      }
    }
    return true
  } catch {
    Message.error('检查退款记录失败')
    return false
  }
}

const goToRefund = async (orderWrapper: OrderWithItems): Promise<void> => {
  const orderItem = orderWrapper.orderItems[0]
  if (!orderItem) return

  if (orderItem.refundStatus) {
    try {
      const response = await authAPI.getRefundByOrderItemId(orderItem.id)
      if (response.success && response.data) {
        router.push({
          name: 'RefundChatStep',
          params: { refundId: String(response.data.id) }
        })
      } else {
        const canApply = await checkRefundCount(orderItem.id)
        if (!canApply) return
        router.push({
          name: 'RefundApply',
          params: { orderItemId: String(orderItem.id) },
          query: {
            orderId: orderWrapper.order.id,
            type: 'REFUND'
          }
        })
      }
    } catch {
      const canApply = await checkRefundCount(orderItem.id)
      if (!canApply) return
      router.push({
        name: 'RefundApply',
        params: { orderItemId: String(orderItem.id) },
        query: {
          orderId: orderWrapper.order.id,
          type: 'REFUND'
        }
      })
    }
    return
  }

  const canApply = await checkRefundCount(orderItem.id)
  if (!canApply) return

  router.push({
    name: 'RefundApply',
    params: { orderItemId: String(orderItem.id) },
    query: {
      orderId: orderWrapper.order.id,
      type: 'REFUND'
    }
  })
}

const goToAfterSale = async (orderWrapper: OrderWithItems): Promise<void> => {
  const orderItem = orderWrapper.orderItems[0]
  if (!orderItem) return

  if (orderItem.refundStatus) {
    try {
      const response = await authAPI.getRefundByOrderItemId(orderItem.id)
      if (response.success && response.data) {
        const refund = response.data
        // 退货退款 + 待退货 → 直接跳转到退货页（未提交退货物流时）
        if (refund.refundType === 'AFTER_SALE' && refund.refundStatus === 'WAITING_RETURN' && !refund.returnStatus) {
          router.push({ name: 'ReturnGoods', params: { refundId: String(refund.id), orderItemId: String(orderItem.id) } })
        } else {
          router.push({
            name: 'RefundChatStep',
            params: { refundId: String(refund.id) }
          })
        }
      } else {
        const canApply = await checkRefundCount(orderItem.id)
        if (!canApply) return
        router.push({
          name: 'RefundApply',
          params: { orderItemId: String(orderItem.id) },
          query: {
            orderId: orderWrapper.order.id,
            type: 'AFTER_SALE'
          }
        })
      }
    } catch {
      const canApply = await checkRefundCount(orderItem.id)
      if (!canApply) return
      router.push({
        name: 'RefundApply',
        params: { orderItemId: String(orderItem.id) },
        query: {
          orderId: orderWrapper.order.id,
          type: 'AFTER_SALE'
        }
      })
    }
    return
  }

  const canApply = await checkRefundCount(orderItem.id)
  if (!canApply) return

  router.push({
    name: 'RefundApply',
    params: { orderItemId: String(orderItem.id) },
    query: {
      orderId: orderWrapper.order.id,
      type: 'AFTER_SALE'
    }
  })
}

const viewReturnLogistics = async (orderItem: OrderItem): Promise<void> => {
  try {
    const response = await authAPI.getRefundByOrderItemId(orderItem.id)
    if (response.success && response.data) {
      router.push({
        name: 'UserLogistics',
        query: {
          refundId: String(response.data.id),
          type: 'return'
        }
      })
    }
  } catch {
    Message.error('获取退款记录失败')
  }
}

const handleAfterSaleAction = (orderWrapper: OrderWithItems): void => {
  const orderItem = orderWrapper.orderItems[0]
  if (!orderItem) return

  if (orderItem.refundStatus === 'RETURNING') {
    viewReturnLogistics(orderItem)
  } else {
    goToAfterSale(orderWrapper)
  }
  toggleMoreActions(orderWrapper.order.id)
}

const getStatusClass = (status: OrderStatus): string => {
  return statusClassMap[status] || ''
}

const getStatusText = (status: OrderStatus): string => {
  return statusTextMap[status] || status
}

const getSellerName = (orderWrapper: OrderWithItems): string => {
  return (orderWrapper as any).sellerName || orderWrapper.orderItems[0]?.sellerName || '商家'
}

const getSellerAvatar = (orderWrapper: OrderWithItems): string => {
  return (orderWrapper as any).sellerAvatar || orderWrapper.orderItems[0]?.sellerAvatar || sellerDefaultAvatar
}

// getRefundStatusText（退款/售后状态中文映射，含 REFUNDING 退款中）统一使用 @/utils/order 公共方法

const formatPrice = (price: number): string => {
  if (price == null || isNaN(price)) {
    return '0.00'
  }
  return price.toFixed(2)
}

// ==================== 滚动加载 ====================

const handleScroll = () => {
  if (loadingMore.value || !hasMore.value) return

  const { scrollTop, scrollHeight, clientHeight } = document.documentElement
  if (scrollTop + clientHeight >= scrollHeight - 100) {
    loadMore()
  }
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value++
  await loadOrders()
}

// ==================== 生命周期 ====================

// 缓存激活时恢复滚动位置
onActivated(() => {
  if (savedScrollTop > 0) {
    setTimeout(() => {
      window.scrollTo({ top: savedScrollTop, behavior: 'instant' })
    }, 50)
  }
})

// 缓存失活时保存滚动位置
onDeactivated(() => {
  savedScrollTop = window.scrollY
})

onMounted(() => {
  if (!authStore.validateUserPermission()) return
  loadOrders()
  window.addEventListener('scroll', handleScroll)
  window.addEventListener('payment-success', handlePaymentSuccess)
  window.addEventListener('refund-update', handleRefundUpdate)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('payment-success', handlePaymentSuccess)
  window.removeEventListener('refund-update', handleRefundUpdate)
})

const handlePaymentSuccess = () => {
  page.value = 1
  hasMore.value = true
  loadOrders()
}

const handleRefundUpdate = () => {
  page.value = 1
  hasMore.value = true
  loadOrders()
}
</script>

<style scoped>
@import url('@/static/css/user/订单列表.css');
</style>
