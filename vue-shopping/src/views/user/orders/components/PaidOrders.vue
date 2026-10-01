<template>
  <div class="order-list">
    <div v-if="loading && orders.length === 0">
      <div v-for="i in 3" :key="i" class="skeleton-card">
        <div class="skeleton-line short"></div>
        <div class="skeleton-line medium" style="margin-top: 12px;"></div>
        <div class="skeleton-line" style="margin-top: 8px;"></div>
        <div class="skeleton-line short" style="margin-top: 12px;"></div>
      </div>
    </div>
    <div v-else-if="orders.length === 0" class="empty-cart">
      <i class="fas fa-shopping-bag"></i>
      <p>暂无订单</p>
      <button class="btn-primary" @click="goShopping">去逛逛</button>
    </div>
    <div v-else>
      <div v-for="orderWrapper in orders" :key="orderWrapper.order.id" class="order-card">
        <div class="order-header">
          <div class="order-header-left" @click.stop="goToShop(orderWrapper)">
            <img :src="getSellerAvatar(orderWrapper)" class="seller-avatar" />
            <span class="seller-name">{{ getSellerName(orderWrapper) }}</span>
          </div>
          <span class="order-status" :class="getOrderStatusClass(orderWrapper.order.status)">
            {{ getOrderStatusText(orderWrapper.order.status) }}
          </span>
        </div>
        <div class="order-items">
          <div v-for="item in orderWrapper.orderItems" :key="item.id" class="order-item" @click="viewOrderDetail(orderWrapper.order.id)">
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
        <div class="order-footer" @click.stop>
          <div class="order-actions">
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
                >{{ getRefundStatusText(orderWrapper.orderItems[0]!.refundStatus) }}</button>
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
          </div>
        </div>
      </div>
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
import { getRefundStatusText } from '@/utils/order'
import sellerDefaultAvatar from '@/static/images/seller-avatar.jpg'

defineOptions({ name: 'PaidOrders' })

const emit = defineEmits(['update-tab-counts'])

const router = useRouter()
const authStore = useAuthStore()

let savedScrollTop = 0

interface OrderItem {
  id: number
  orderId: number
  productId: number
  sellerId: number
  productName: string
  productImage: string
  skuName?: string
  quantity: number
  price: number
  refundStatus?: string
  sellerName?: string
  sellerAvatar?: string
}

interface Order {
  id: number
  orderNumber: string
  totalAmount: number
  paidAt?: string
  status: string
  orderItems: OrderItem[]
}

interface OrderWithItems {
  order: Order
  orderItems: OrderItem[]
}

const orders = ref<OrderWithItems[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const hasMore = ref(true)
const loadingMore = ref(false)

const loadOrders = async (): Promise<void> => {
  if (loading.value) return
  loading.value = true
  try {
    const params = {
      page: page.value,
      pageSize: pageSize.value,
      status: ['PAID', 'PROCESSING']
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
      emit('update-tab-counts', {
        all: data.counts?.total || 0,
        PENDING: data.counts?.PENDING || 0,
        PAID: (data.counts?.PAID || 0) + (data.counts?.PROCESSING || 0),
        SHIPPED: data.counts?.SHIPPED || 0,
        COMPLETED: data.counts?.COMPLETED || 0
      })
    }
  } catch (error: any) {
    Message.error(error.message || '加载失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const getOrderStatusText = (status: string): string => {
  const map: Record<string, string> = {
    'PAID': '待发货',
    'PROCESSING': '待发货'
  }
  return map[status] || status
}

const getOrderStatusClass = (status: string): string => {
  const map: Record<string, string> = {
    'PAID': 'status-paid',
    'PROCESSING': 'status-processing'
  }
  return map[status] || 'status-paid'
}

const canRefund = (order: Order): boolean => {
  if (!order.paidAt) return false
  const paidTime = new Date(order.paidAt).getTime()
  const now = Date.now()
  const days = (now - paidTime) / (1000 * 60 * 60 * 24)
  return days <= 7
}

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

const goToRefund = async (orderWrapper: OrderWithItems) => {
  const orderItem = orderWrapper.orderItems[0]
  if (!orderItem) return

  if (orderItem.refundStatus) {
    try {
      const response = await authAPI.getRefundByOrderItemId(orderItem.id)
      if (response.success && response.data) {
        const refund = response.data
        if (refund.refundType === 'AFTER_SALE' && refund.refundStatus === 'WAITING_RETURN' && !refund.returnStatus) {
          router.push({ name: 'ReturnGoods', params: { refundId: String(refund.id), orderItemId: String(orderItem.id) } })
        } else {
          router.push({ name: 'RefundChatStep', params: { refundId: String(refund.id) } })
        }
      }
    } catch {
      // ignore
    }
    return
  }

  const canApply = await checkRefundCount(orderItem.id)
  if (!canApply) return

  router.push({ name: 'RefundApply', params: { orderItemId: String(orderItem.id) }, query: { orderId: orderWrapper.order.id } })
}

const goToAfterSale = async (orderWrapper: OrderWithItems) => {
  const orderItem = orderWrapper.orderItems[0]
  if (!orderItem) return

  if (orderItem.refundStatus) {
    try {
      const response = await authAPI.getRefundByOrderItemId(orderItem.id)
      if (response.success && response.data) {
        const refund = response.data
        if (refund.refundType === 'AFTER_SALE' && refund.refundStatus === 'WAITING_RETURN' && !refund.returnStatus) {
          router.push({ name: 'ReturnGoods', params: { refundId: String(refund.id), orderItemId: String(orderItem.id) } })
        } else {
          router.push({ name: 'RefundChatStep', params: { refundId: String(refund.id) } })
        }
      }
    } catch {
      // ignore
    }
    return
  }

  const canApply = await checkRefundCount(orderItem.id)
  if (!canApply) return

  router.push({ name: 'RefundApply', params: { orderItemId: String(orderItem.id) }, query: { orderId: orderWrapper.order.id } })
}

const viewOrderDetail = (orderId: number) => {
  router.push({ name: 'OrderDetail', params: { orderId } })
}

const goToShop = (orderWrapper: any) => {
  const sellerId = orderWrapper.orderItems[0]?.sellerId
  if (sellerId) {
    router.push({ name: 'Shop', params: { sellerId } })
  }
}

const viewProduct = (productId: number) => {
  router.push({ name: 'ProductDetail', params: { productId } })
}

const goShopping = () => {
  router.push({ name: 'UserDashboard' })
}

const getSellerName = (orderWrapper: OrderWithItems): string => {
  return (orderWrapper as any).sellerName || orderWrapper.orderItems[0]?.sellerName || '商家'
}

const getSellerAvatar = (orderWrapper: OrderWithItems): string => {
  return (orderWrapper as any).sellerAvatar || orderWrapper.orderItems[0]?.sellerAvatar || sellerDefaultAvatar
}

// getRefundStatusText（退款/售后状态中文映射，含 REFUNDING 退款中）统一使用 @/utils/order 公共方法

const formatPrice = (price: number) => (price || 0).toFixed(2)

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

onActivated(() => {
  if (savedScrollTop > 0) {
    setTimeout(() => {
      window.scrollTo({ top: savedScrollTop, behavior: 'instant' })
    }, 50)
  }
})

onDeactivated(() => {
  savedScrollTop = window.scrollY
})

onMounted(() => {
  if (!authStore.validateUserPermission()) return
  loadOrders()
  window.addEventListener('scroll', handleScroll)
  window.addEventListener('refund-update', handleRefundUpdate)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('refund-update', handleRefundUpdate)
})

const handleRefundUpdate = () => {
  page.value = 1
  hasMore.value = true
  loadOrders()
}
</script>

<style scoped>
@import url('@/static/css/user/订单列表.css');
</style>
