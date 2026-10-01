<template>
  <div class="order-detail-page">
    <!-- ========== 页面头部区域 ========== -->
    <div class="page-navbar">
      <button class="page-nav-back" @click="goBack">
        <i class="fas fa-chevron-left"></i>
      </button>
      <div class="page-nav-title">
        <span>订单详情</span>
      </div>
      <div class="page-nav-right"></div>
    </div>

    <!-- ========== 加载状态 ========== -->
    <div v-if="loading" class="skeleton-detail">
      <div class="skeleton-section">
        <div class="skeleton skeleton-section-title"></div>
        <div class="skeleton-section-content">
          <div class="skeleton skeleton-line long"></div>
          <div class="skeleton skeleton-line medium"></div>
          <div class="skeleton skeleton-line short"></div>
        </div>
      </div>
      <div class="skeleton-section">
        <div class="skeleton skeleton-section-title"></div>
        <div class="skeleton-section-content">
          <div class="skeleton" style="height: 80px; margin-bottom: 10px;"></div>
        </div>
      </div>
      <div class="skeleton-section">
        <div class="skeleton skeleton-section-title"></div>
        <div class="skeleton-order-list">
          <div v-for="i in 2" :key="i" class="skeleton-order-card">
            <div class="skeleton-order-goods">
              <div class="skeleton" style="width: 80px; height: 80px; border-radius: 8px;"></div>
              <div style="flex: 1;">
                <div class="skeleton skeleton-line long" style="margin-bottom: 10px;"></div>
                <div class="skeleton skeleton-line medium"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 订单详情内容 ========== -->
    <div v-else-if="orderData" class="detail-content content-wrapper">

      <!-- ========== 物流轨迹栏 ========== -->
      <div class="logistics-bar" @click="viewLogistics" v-if="latestTrace">
        <div class="logistics-icon">
          <i class="fas fa-truck"></i>
        </div>
        <div class="logistics-content">
          <span class="trace-text">{{ latestTrace.status || latestTrace.description }}</span>
          <span class="trace-time">{{ formatDateTime(latestTrace.time) }}</span>
        </div>
        <div class="logistics-arrow">
          <i class="fas fa-chevron-right"></i>
        </div>
      </div>

      <!-- ========== 收货信息卡片 ========== -->
      <div class="address-card" v-if="addressData">
        <div class="address-icon">
          <i class="fas fa-map-marker-alt"></i>
        </div>
        <div class="address-content">
          <div class="address-detail" @click="copyAddress">
            {{ formatAddress(addressData) }}
            <i class="fas fa-copy copy-icon"></i>
          </div>
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

      <!-- ========== 商品清单卡片 ========== -->
      <div class="detail-card">
        <div class="card-header">
          <i class="fas fa-boxes"></i>
          <span class="card-title">商品清单</span>
        </div>
        <div class="card-body no-padding">
          <div class="product-list">
            <div v-for="item in orderItems" :key="item.id" class="product-item">
              <img :src="item.productImage || defaultProductImage" class="product-image" @click="previewProductImage(orderItems, item.productImage)">
              <div class="product-info">
                <div class="product-name">{{ item.productName }}</div>
                <div class="tags-group">
                  <span v-if="item.skuName" class="tag-spec">{{ item.skuName }}</span>
                  <span class="tag-quantity">x{{ item.quantity }}</span>
                </div>
              </div>
              <div class="product-right">
                <button
                  v-if="item.refundStatus"
                  class="action-btn outline refund-btn"
                  @click="viewAfterSale(item)"
                >
                  查看售后
                </button>
                <div class="product-price">¥{{ formatPrice(item.price) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 金额汇总卡片 ========== -->
      <div class="amount-card">
        <div class="amount-row">
          <span class="amount-label">商品总额</span>
          <span class="amount-value">¥{{ formatPrice(orderData.totalAmount) }}</span>
        </div>
        <div class="amount-row total">
          <span class="amount-label">实付款</span>
          <span class="amount-value total-price">¥{{ formatPrice(orderData.totalAmount) }}</span>
        </div>
      </div>

      <!-- ========== 订单信息卡片（折叠） ========== -->
      <div class="detail-card collapse-card">
        <div class="collapse-header" @click="orderInfoCollapsed = !orderInfoCollapsed">
          <div class="card-header">
            <i class="fas fa-file-alt"></i>
            <span class="card-title">订单信息</span>
          </div>
          <i class="fas" :class="orderInfoCollapsed ? 'fa-chevron-down' : 'fa-chevron-up'"></i>
        </div>
        <div class="collapse-content" v-show="!orderInfoCollapsed">
          <div class="info-row">
            <span class="info-label">订单号：</span>
            <span class="info-value copy-value">
              {{ orderData.orderNumber }}
              <button class="copy-btn" @click="copyOrderInfo">
                <i class="fas fa-copy"></i>
              </button>
            </span>
          </div>
          <div class="info-row">
            <span class="info-label">支付方式：</span>
            <span class="info-value">{{ getPaymentMethodText(orderData.paymentMethod) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">下单时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.createdAt) }}</span>
          </div>
          <div v-if="orderData.paidAt" class="info-row">
            <span class="info-label">支付时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.paidAt) }}</span>
          </div>
          <div v-if="orderData.processingAt" class="info-row">
            <span class="info-label">处理时间：</span>
            <span class="info-value">{{ formatDateTime(orderData.processingAt) }}</span>
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

      <!-- 底部留空防止被固定操作栏遮挡 -->
      <div class="bottom-space"></div>
    </div>

    <!-- ========== 底部固定操作栏 ========== -->
    <div v-if="showBottomBar" class="order-footer">
      <div class="order-actions">
        <!-- 联系买家 -->
        <button class="btn-outline" @click="contactBuyer">
          <i class="fas fa-headset"></i>
          <span>联系买家</span>
        </button>

        <!-- PAID：处理订单 -->
        <template v-if="orderData?.status === 'PAID'">
          <button class="btn-outline" @click="processOrder">处理订单</button>
        </template>
        <!-- PROCESSING：发货 -->
        <template v-if="orderData?.status === 'PROCESSING'">
          <button class="btn-outline" @click="openShipDialog">发货</button>
        </template>
        <!-- SHIPPED：查看物流 -->
        <template v-if="orderData?.status === 'SHIPPED'">
          <button class="btn-outline" @click="viewLogistics">查看物流</button>
        </template>
      </div>
    </div>

    <!-- ========== 发货弹窗 ========== -->
    <div v-if="showShipDialog" class="modal-overlay" @click="closeShipDialog">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>发货</h3>
          <button class="modal-close" @click="closeShipDialog"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label required">物流单号</label>
            <input type="text" v-model="shipForm.trackingNumber" class="form-control" placeholder="请输入物流单号" />
          </div>
          <div class="form-group">
            <label class="form-label required">物流公司</label>
            <select v-model="shipForm.logisticsCode" class="form-control">
              <option value="">请选择物流公司</option>
              <option value="SF">顺丰速运</option>
              <option value="YTO">圆通速递</option>
              <option value="ZTO">中通快递</option>
              <option value="EMS">邮政EMS</option>
              <option value="YD">韵达快递</option>
              <option value="STO">申通快递</option>
              <option value="JT">极兔速递</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" @click="closeShipDialog">取消</button>
          <button class="btn-confirm" @click="confirmShip" :disabled="submitting">{{ submitting ? '发货中...' : '确认发货' }}</button>
        </div>
      </div>
    </div>

    <!-- 退款记录选择弹窗 -->
    <div v-if="showRefundSelectDialog" class="modal-overlay" @click="closeRefundSelectDialog">
      <div class="modal-content refund-selector-dialog" @click.stop>
        <div class="dialog-header">
          <span>选择退款项</span>
          <button class="dialog-close" @click="closeRefundSelectDialog">
            <i class="fas fa-times"></i>
          </button>
        </div>
        <div class="dialog-body">
          <div v-if="refundRecords.length === 0" class="empty-records">
            暂无退款记录
          </div>
          <div v-else class="record-list">
            <div
              v-for="(record, index) in refundRecords"
              :key="record.id"
              class="record-item refund-record-item"
              :class="{ selected: selectedRefundId === record.id }"
              @click="selectedRefundId = record.id"
            >
              <div class="record-left">
                <div class="record-title">
                  第{{ refundRecords.length - index }}次申请
                </div>
                <div class="record-meta">
                  <span>{{ record.refundType === 'REFUND' ? '仅退款' : '退货退款' }}</span>
                  <span class="meta-divider">·</span>
                  <span>{{ getRefundStatusText(record.refundStatus, record.refundType, record.returnStatus) }}</span>
                </div>
                <div class="record-time">{{ formatDateTime(record.applyTime) }}</div>
              </div>
              <div class="record-right">
                <div class="record-amount">¥{{ formatPrice(record.refundAmount) }}</div>
                <div class="record-radio">
                  <i v-if="selectedRefundId === record.id" class="fas fa-check-circle"></i>
                  <i v-else class="far fa-circle"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="closeRefundSelectDialog">取消</button>
          <button class="btn-confirm" @click="confirmSelectRefund" :disabled="!selectedRefundId">
            确认查看
          </button>
        </div>
      </div>
    </div>

    <!-- 图片预览组件 -->
    <ImagePreview
      v-if="showImagePreview"
      :mediaList="previewMediaList"
      :currentIndex="previewCurrentIndex"
      @close="showImagePreview = false"
      @update:index="previewCurrentIndex = $event"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import { useAuthStore } from '@/stores/auth'
import { getRefundStatusText } from '@/utils/order'
import ImagePreview from '@/components/ImagePreview.vue'
import sellerDefaultAvatar from '@/static/images/seller-avatar.jpg'
import defaultProductImage from '@/static/images/云杉购图标.jpg'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// ==================== 类型定义 ====================

type OrderStatus = 'PENDING' | 'PAID' | 'PROCESSING' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED'
type PaymentMethod = 'ALIPAY' | 'WECHAT'
type OrderSource = 'cart' | 'product'

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
  isReviewed: boolean
  reviewedAt: string | null
  createdAt: string
  refundStatus?: string
  skuName?: string
  refundType?: string
  returnStatus?: string
}

interface RefundRecord {
  id: number
  refundStatus: string
  refundAmount: number
  applyTime: string
  refundType: string
  returnStatus?: string
  isActive?: boolean
}

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
  processingAt: string | null
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
}

interface OrderDetailData {
  order: Order
  orderItems: OrderItem[]
  address: Address
  sellerName?: string
  sellerAvatar?: string
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

interface ShipForm {
  trackingNumber: string
  logisticsCode: string
}

interface StatusConfig {
  text: string
  desc: string
  icon: string
  class: string
}

// ==================== 响应式数据 ====================

const loading = ref(true)
const submitting = ref(false)
const showShipDialog = ref(false)

const orderData = ref<Order | null>(null)
const orderItems = ref<OrderItem[]>([])
const addressData = ref<Address | null>(null)

const shipForm = ref<ShipForm>({
  trackingNumber: '',
  logisticsCode: ''
})

const showImagePreview = ref(false)
const previewCurrentIndex = ref(0)
const previewMediaList = ref<{ type: 'image' | 'video'; url: string; cover?: string }[]>([])

// 物流信息
const latestTrace = ref<LogisticsTrace | null>(null)

// 折叠状态
const orderInfoCollapsed = ref(true)
const logisticsCollapsed = ref(true)

// 手机号显示状态（默认隐匿）
const showFullPhone = ref(false)

// 退款记录选择弹窗
const showRefundSelectDialog = ref(false)
const refundRecords = ref<RefundRecord[]>([])
const currentOrderItemId = ref<number | null>(null)
const selectedRefundId = ref<number | null>(null)

// ==================== 计算属性 ====================

/**
 * 判断是否显示底部操作栏
 * PENDING - 取消订单
 * PAID - 处理订单
 * PROCESSING - 发货
 * SHIPPED - 查看物流
 * 其他状态 - 不显示
 */
const showBottomBar = computed(() => {
  if (!orderData.value) return false
  return true
})

// ==================== 静态配置 ====================

const logisticsMap: Record<string, string> = {
  SF: '顺丰速运',
  YTO: '圆通速递',
  ZTO: '中通快递',
  EMS: '邮政EMS',
  YD: '韵达快递',
  STO: '申通快递',
  JT: '极兔速递'
}

const statusConfig: Record<OrderStatus, StatusConfig> = {
  PENDING: {
    text: '待付款',
    desc: '等待买家付款',
    icon: 'fas fa-clock',
    class: 'status-pending'
  },
  PAID: {
    text: '待发货',
    desc: '买家已付款，请及时处理',
    icon: 'fas fa-check-circle',
    class: 'status-paid'
  },
  PROCESSING: {
    text: '待发货',
    desc: '订单已确认，正在备货中',
    icon: 'fas fa-spinner',
    class: 'status-processing'
  },
  SHIPPED: {
    text: '待收货',
    desc: '商品已发出，等待买家收货',
    icon: 'fas fa-truck',
    class: 'status-shipped'
  },
  COMPLETED: {
    text: '已完成',
    desc: '订单已完成，感谢您的支持',
    icon: 'fas fa-check-double',
    class: 'status-completed'
  },
  CANCELLED: {
    text: '已取消',
    desc: '订单已取消',
    icon: 'fas fa-times-circle',
    class: 'status-cancelled'
  }
}

const paymentMethodMap: Record<PaymentMethod, string> = {
  ALIPAY: '支付宝',
  WECHAT: '微信支付'
}

// ==================== 数据加载 ====================

/**
 * 加载订单详情
 * @returns {Promise<void>}
 */
const loadOrderDetail = async (): Promise<void> => {
  loading.value = true

  try {
    const orderId = route.params.id

    if (!orderId) {
      Message.error('订单ID不存在')
      router.push({ name: 'SellerOrders' })
      return
    }

    const response = await authAPI.getSellerOrderDetail(Number(orderId))

    if (response.success && response.data?.orderDetail) {
      const data = response.data.orderDetail as OrderDetailData

      orderData.value = {
        ...data.order,
        sellerName: data.sellerName,
        sellerAvatar: data.sellerAvatar
      }
      orderItems.value = data.orderItems.map(item => ({
        ...item,
        totalPrice: item.price * item.quantity
      }))
      addressData.value = data.address

      // 加载物流信息
      await loadLogisticsInfo()
    } else {
      Message.error(response.message || '获取订单详情失败')
    }
  } catch (error: any) {
    Message.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 加载物流信息
 * @returns {Promise<void>}
 */
const loadLogisticsInfo = async (): Promise<void> => {
  if (!orderData.value?.id) return

  const status = orderData.value.status

  if (status === 'PENDING') {
    latestTrace.value = {
      status: '订单已创建，等待付款',
      time: orderData.value.createdAt || '',
      description: '',
      location: ''
    }
  } else {
    try {
      const response = await authAPI.getLogisticsInfo(orderData.value.id)
      if (response.success) {
        const traces = response.data?.traces
                    || response.data?.logistics?.traces
                    || response.data?.result?.traces
                    || response.data?.result?.logistics?.traces
        if (traces && traces.length > 0) {
          latestTrace.value = traces[0]
        } else {
          latestTrace.value = {
            status: getDefaultTraceStatus(status),
            time: orderData.value.shippedAt || orderData.value.paidAt || orderData.value.createdAt || '',
            description: '',
            location: ''
          }
        }
      } else {
        latestTrace.value = null
      }
    } catch (error) {
      console.error('加载物流信息失败', error)
      latestTrace.value = null
    }
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
    case 'SHIPPED':
      return '订单待收货'
    case 'COMPLETED':
      return '订单已完成'
    case 'CANCELLED':
      return '订单已取消'
    default:
      return '订单已提交'
  }
}


// ==================== 订单操作 ====================

/**
 * 处理订单（PAID → PROCESSING）
 * @returns {Promise<void>}
 */
const processOrder = async (): Promise<void> => {
  try {
    await Message.confirm('确定要处理该订单吗？处理后将进入备货状态。', '处理订单')

    const response = await authAPI.processOrder(orderData.value!.id)

    if (response.success) {
      Message.success('订单已处理')
      await loadOrderDetail()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      Message.error(error.message || '操作失败')
    }
  }
}

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
      Message.error(error.message || '操作失败')
    }
  }
}

// ==================== 发货相关 ====================

const openShipDialog = (): void => {
  shipForm.value = { trackingNumber: '', logisticsCode: '' }
  showShipDialog.value = true
}

const closeShipDialog = (): void => {
  showShipDialog.value = false
  shipForm.value = { trackingNumber: '', logisticsCode: '' }
}

/**
 * 确认发货
 * @returns {Promise<void>}
 */
const confirmShip = async (): Promise<void> => {
  const trackingNumber = shipForm.value.trackingNumber.trim()

  if (!trackingNumber) {
    Message.error('请输入物流单号')
    return
  }

  if (!/^[a-zA-Z0-9-]{8,30}$/.test(trackingNumber)) {
    Message.error('物流单号格式不正确（8-30位字母数字或-）')
    return
  }

  if (!shipForm.value.logisticsCode) {
    Message.error('请选择物流公司')
    return
  }

  submitting.value = true

  try {
    const data = {
      trackingNumber: shipForm.value.trackingNumber,
      logisticsCode: shipForm.value.logisticsCode,
      logisticsName: logisticsMap[shipForm.value.logisticsCode] || ''
    }

    const orderId = orderData.value?.id
    if (!orderId) {
      Message.error('订单信息不存在')
      return
    }
    const response = await authAPI.shipOrder(orderId, data)

    if (response.success) {
      Message.success('发货成功')
      closeShipDialog()
      await loadOrderDetail()
    }
  } catch (error: any) {
    Message.error(error.message || '发货失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 页面跳转 ====================

const viewLogistics = (): void => {
  const orderId = orderData.value?.id
  const userId = orderData.value?.userId
  router.push({
    name: 'SellerLogistics',
    query: {
      orderId: String(orderId),
      userId: String(userId)
    }
  })
}

/**
 * 查看退款进度
 * @param item - 订单商品项
 */
const viewProduct = (productId: number): void => {
  router.push({ name: 'ProductDetail', params: { productId } })
}

/**
 * 查看售后 - 获取退款记录列表，支持多记录选择
 */
const viewAfterSale = async (item: OrderItem): Promise<void> => {
  try {
    const response = await authAPI.getSellerRefundsByOrderItem(item.id)
    if (response.success && response.data) {
      const records = response.data as RefundRecord[]
      if (records.length === 0) {
        Message.error('暂无退款记录')
        return
      }
      // 无论几条记录，都弹窗让商家选择
      refundRecords.value = records
      selectedRefundId.value = records[0]?.id || null
      showRefundSelectDialog.value = true
    } else {
      Message.error('暂无退款记录')
    }
  } catch (error) {
    Message.error('获取退款记录失败')
  }
}

/**
 * 关闭退款记录选择弹窗
 */
const closeRefundSelectDialog = (): void => {
  showRefundSelectDialog.value = false
  refundRecords.value = []
  selectedRefundId.value = null
}

/**
 * 确认选择退款记录并跳转
 */
const confirmSelectRefund = (): void => {
  if (!selectedRefundId.value) return
  showRefundSelectDialog.value = false
  router.push({
    name: 'RefundChatStep',
    params: { refundId: String(selectedRefundId.value) }
  })
}

/**
 * 获取退款状态显示文字
 * 统一使用 @/utils/order 的 getRefundStatusText，含 REFUNDING 退款中等全部映射。
 */

const previewProductImage = (items: OrderItem[], clickedImage: string): void => {
  previewMediaList.value = items.map(item => ({
    type: 'image' as const,
    url: item.productImage || defaultProductImage
  }))
  const index = previewMediaList.value.findIndex(item => item.url === clickedImage)
  previewCurrentIndex.value = index >= 0 ? index : 0
  showImagePreview.value = true
}

const goBack = (): void => {
  router.back()
}

const contactBuyer = () => {
  const userId = orderData.value?.userId
  if (!userId) {
    Message.error('无法获取买家信息')
    return
  }
  router.push({ name: 'Chat', params: { targetId: userId } })
}

// ==================== 工具函数 ====================

const getStatusConfig = (status: OrderStatus): StatusConfig => {
  return statusConfig[status] || statusConfig.PENDING
}

const getStatusClass = (status: OrderStatus): string => {
  return getStatusConfig(status).class
}

const getStatusText = (status: OrderStatus): string => {
  return getStatusConfig(status).text
}

const getStatusDesc = (status: OrderStatus): string => {
  return getStatusConfig(status).desc
}

const getStatusIcon = (status: OrderStatus): string => {
  return getStatusConfig(status).icon
}

const getPaymentMethodText = (method: PaymentMethod | null): string => {
  if (!method) return '-'
  return paymentMethodMap[method] || method
}

const formatPrice = (price: number): string => {
  if (price == null) return '0.00'
  return price.toFixed(2)
}

const formatDateTime = (dateStr: string | null): string => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

const formatAddress = (address: Address | null): string => {
  if (!address) return '-'
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
 * 复制收货地址
 */
const copyAddress = (): void => {
  if (!addressData.value) return
  const address = `${addressData.value.recipientName} ${addressData.value.recipientPhone}\n${formatAddress(addressData.value)}`
  navigator.clipboard.writeText(address).then(() => {
    Message.success('已复制')
  }).catch(() => {
    Message.error('复制失败')
  })
}

/**
 * 复制订单信息
 */
const copyOrderInfo = (): void => {
  if (!orderData.value) return
  const info = `订单号：${orderData.value.orderNumber}\n下单时间：${formatDateTime(orderData.value.createdAt)}\n支付方式：${getPaymentMethodText(orderData.value.paymentMethod)}`
  navigator.clipboard.writeText(info).then(() => {
    Message.success('已复制')
  }).catch(() => {
    Message.error('复制失败')
  })
}

// ==================== 生命周期 ====================

const handleReturnSubmitted = () => {
  loadOrderDetail()
}

onMounted(() => {
  if (!authStore.validateSellerPermission()) return
  loadOrderDetail()
  window.addEventListener('return-submitted', handleReturnSubmitted)
})

onUnmounted(() => {
  window.removeEventListener('return-submitted', handleReturnSubmitted)
})
</script>

<style scoped>
@import url('@/static/css/seller/订单详情页.css');
</style>
