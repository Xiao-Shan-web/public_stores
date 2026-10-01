<template>
  <div class="order-list-wrapper">
    <div v-if="loading" class="skeleton-order-list">
      <div v-for="i in 5" :key="i" class="skeleton-order-card">
        <div class="skeleton-order-header">
          <div class="skeleton" style="width: 150px; height: 14px;"></div>
          <div class="skeleton" style="width: 80px; height: 14px;"></div>
        </div>
        <div class="skeleton-order-goods">
          <div class="skeleton" style="width: 80px; height: 80px; border-radius: 8px;"></div>
          <div style="flex: 1;">
            <div class="skeleton skeleton-line long" style="margin-bottom: 10px;"></div>
            <div class="skeleton skeleton-line medium"></div>
          </div>
        </div>
        <div class="skeleton-order-footer">
          <div class="skeleton" style="width: 80px; height: 32px; border-radius: 6px;"></div>
        </div>
      </div>
    </div>

    <div v-else-if="orders.length === 0" class="empty-state">
      <i class="fas fa-shopping-bag"></i>
      <p>暂无待付款订单</p>
    </div>

    <div v-else class="order-list content-wrapper">
      <SellerOrderList
        v-for="orderWrapper in orders"
        :key="orderWrapper.order.id"
        :order-wrapper="orderWrapper"
        @view-detail="viewOrderDetail"
        @process-order="processOrder"
        @ship-order="openShipDialog"
        @view-logistics="viewLogistics"
        @handle-refund="handleRefund"
      />
    </div>

    <div class="pagination" v-if="total > 0">
      <div class="pagination-left">
        <button class="page-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
          <i class="fas fa-chevron-left"></i>
        </button>
        <div class="page-numbers">
          <button v-if="currentPage > 3" class="page-number-btn" @click="changePage(1)">1</button>
          <span v-if="currentPage > 4" class="page-ellipsis">...</span>
          <button v-for="page in visiblePages" :key="page" class="page-number-btn" :class="{ active: page === currentPage }" @click="changePage(page)">{{ page }}</button>
          <span v-if="currentPage < totalPages - 3" class="page-ellipsis">...</span>
          <button v-if="currentPage < totalPages - 2" class="page-number-btn" @click="changePage(totalPages)">{{ totalPages }}</button>
        </div>
        <button class="page-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
          <i class="fas fa-chevron-right"></i>
        </button>
      </div>
      <div class="pagination-right">
        <div class="page-jump">
          <span>跳转到</span>
          <input type="number" v-model="jumpPage" class="jump-input" min="1" :max="totalPages" @keyup.enter="handleJump" placeholder="页码" />
          <span>页</span>
          <button class="jump-btn" @click="handleJump">确定</button>
        </div>
        <select v-model="pageSize" class="page-size-select" @change="handlePageSizeChange">
          <option :value="20">20条/页</option>
          <option :value="50">50条/页</option>
          <option :value="100">100条/页</option>
        </select>
        <span class="total-info">共 {{ total }} 条订单</span>
      </div>
    </div>

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

    <div v-if="showRefundDialog" class="modal-overlay" @click.self="closeRefundDialog">
      <div class="modal-content refund-selector-dialog" @click.stop>
        <div class="dialog-header">
          <span>选择退款项</span>
          <button class="dialog-close" @click="closeRefundDialog"><i class="fas fa-times"></i></button>
        </div>
        <div class="dialog-body">
          <div v-if="refundingItemList.length === 0" class="empty-records">暂无退款记录</div>
          <div v-else class="record-list">
            <div v-for="item in refundingItemList" :key="item.id" class="order-item-row">
              <div class="order-item-header" @click="toggleItemExpand(item.id)">
                <div class="record-left">
                  <div class="record-title">{{ item.productName }}</div>
                  <div v-if="item.skuName" class="record-sku">{{ item.skuName }}</div>
                </div>
                <div class="record-right">
                  <div class="record-amount">¥{{ formatPrice(item.price * item.quantity) }}</div>
                  <div class="expand-icon"><i :class="expandedItems.has(item.id) ? 'fas fa-chevron-down' : 'fas fa-chevron-right'"></i></div>
                </div>
              </div>
              <div v-if="expandedItems.has(item.id)" class="refund-records-expand">
                <div v-if="itemRefundRecords[item.id]?.length === 0" class="empty-records expand-empty">暂无退款记录</div>
                <div v-else-if="itemRefundRecords[item.id]">
                  <div v-for="(record, index) in itemRefundRecords[item.id]" :key="record.id" class="record-item refund-record-item" :class="{ selected: selectedRefundId === record.id }" @click="selectRefundRecord(record.id)">
                    <div class="record-left">
                      <div class="record-title">第{{ (itemRefundRecords[item.id]?.length ?? 0) - index }}次申请</div>
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
                <div v-else class="loading-refund-records"><i class="fas fa-spinner fa-spin"></i> 加载中...</div>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="closeRefundDialog">取消</button>
          <button class="btn-confirm" @click="confirmSelectRefund" :disabled="!selectedRefundId">确认查看</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue'
  import { useRouter } from 'vue-router'
  import { authAPI } from '@/api/authAPI'
  import Message from '@/utils/message'
  import SellerOrderList from './SellerOrderList.vue'
  import { getRefundStatusText } from '@/utils/order'

  const router = useRouter()

  interface OrderItem {
    id: number
    productId: number
    productName: string
    productImage: string
    quantity: number
    price: number
    refundStatus?: string
    refundType?: string
    skuName?: string
    sellerName?: string
    sellerAvatar?: string
  }

  interface Order {
    id: number
    orderNumber: string
    totalAmount: number
    status: string
    createdAt: string
  }

  interface OrderWithItems {
    order: Order
    orderItems: OrderItem[]
    sellerName?: string
    sellerAvatar?: string
  }

  const loading = ref(true)
  const orders = ref<OrderWithItems[]>([])
  const currentPage = ref(1)
  const pageSize = ref(10)
  const totalPages = ref(1)
  const total = ref(0)
  const jumpPage = ref('')

  const showShipDialog = ref(false)
  const currentOrderId = ref<number | null>(null)
  const submitting = ref(false)
  const shipForm = ref({ trackingNumber: '', logisticsCode: '' })

  const showRefundDialog = ref(false)
  const refundingItemList = ref<OrderItem[]>([])
  const expandedItems = ref<Set<number>>(new Set())
  const itemRefundRecords = ref<Record<number, Array<{ id: number; refundType: string; refundStatus: string; returnStatus?: string; refundAmount: number; applyTime: string }>>>({})
  const selectedRefundId = ref<number | null>(null)

  const logisticsMap: Record<string, string> = {
    SF: '顺丰速运', YTO: '圆通速递', ZTO: '中通快递',
    EMS: '邮政EMS', YD: '韵达快递', STO: '申通快递', JT: '极兔速递'
  }

  const visiblePages = computed(() => {
    const pages: number[] = []
    const start = Math.max(1, currentPage.value - 2)
    const end = Math.min(totalPages.value, currentPage.value + 2)
    for (let i = start; i <= end; i++) {
      if (!pages.includes(i)) pages.push(i)
    }
    return pages
  })

  const loadOrders = async () => {
    loading.value = true
    try {
      const params = { page: currentPage.value, pageSize: pageSize.value, status: 'PENDING' }
      const response = await authAPI.getSellerOrders(params)
      if (response.success) {
        const data = response.data || {}
        orders.value = data.records || []
        totalPages.value = data.totalPages || 1
        total.value = data.total || 0
      }
    } catch (error: any) {
      Message.error(error.message || '加载失败')
    } finally {
      loading.value = false
    }
  }

  const changePage = (page: number) => {
    if (page < 1 || page > totalPages.value) return
    currentPage.value = page
    window.scrollTo({ top: 0, behavior: 'smooth' })
    loadOrders()
  }

  const handleJump = () => {
    const page = parseInt(jumpPage.value)
    if (!isNaN(page) && page >= 1 && page <= totalPages.value) {
      changePage(page)
    } else {
      Message.error('请输入有效的页码')
    }
    jumpPage.value = ''
  }

  const handlePageSizeChange = () => {
    currentPage.value = 1
    loadOrders()
  }

  const viewOrderDetail = (orderId: number) => {
    router.push({ name: 'SellerOrderDetail', params: { id: orderId } })
  }

  const processOrder = async (orderId: number) => {
    try {
      await Message.confirm('确定要处理该订单吗？', '处理订单')
      const response = await authAPI.processOrder(orderId)
      if (response.success) {
        Message.success('订单已处理')
        await loadOrders()
      }
    } catch (error: any) {
      if (error !== 'cancel') Message.error(error.message || '处理订单失败')
    }
  }

  const openShipDialog = (orderId: number) => {
    currentOrderId.value = orderId
    shipForm.value = { trackingNumber: '', logisticsCode: '' }
    showShipDialog.value = true
  }

  const closeShipDialog = () => {
    showShipDialog.value = false
    currentOrderId.value = null
    shipForm.value = { trackingNumber: '', logisticsCode: '' }
  }

  const confirmShip = async () => {
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
      const orderId = currentOrderId.value
      if (!orderId) { Message.error('订单ID不存在'); return }
      const data = { trackingNumber: shipForm.value.trackingNumber, logisticsCode: shipForm.value.logisticsCode, logisticsName: logisticsMap[shipForm.value.logisticsCode] || '未知物流' }
      const response = await authAPI.shipOrder(orderId, data)
      if (response.success) {
        Message.success('发货成功')
        closeShipDialog()
        await loadOrders()
      }
    } catch (error: any) {
      Message.error(error.message || '发货失败')
    } finally {
      submitting.value = false
    }
  }

  const viewLogistics = (orderId: number) => {
    router.push({ name: 'SellerLogistics', query: { orderId: String(orderId) } })
  }

  const hasRefundingItem = (orderWrapper: OrderWithItems): boolean => {
    return orderWrapper.orderItems.some(item =>
      item.refundStatus === 'PROCESSING' || item.refundStatus === 'WAITING_RETURN' ||
      item.refundStatus === 'RETURNING' || item.refundStatus === 'SUCCESS' || item.refundStatus === 'FAILED'
    )
  }

  const handleRefund = (orderWrapper: OrderWithItems) => {
    const refundingItems = orderWrapper.orderItems.filter(item =>
      item.refundStatus === 'PROCESSING' || item.refundStatus === 'WAITING_RETURN' ||
      item.refundStatus === 'RETURNING' || item.refundStatus === 'SUCCESS' || item.refundStatus === 'FAILED'
    )
    if (refundingItems.length === 0) { Message.error('没有待处理的退款'); return }
    refundingItemList.value = refundingItems
    expandedItems.value = new Set()
    itemRefundRecords.value = {}
    selectedRefundId.value = null
    showRefundDialog.value = true
  }

  const toggleItemExpand = async (itemId: number) => {
    const newExpanded = new Set(expandedItems.value)
    if (newExpanded.has(itemId)) { newExpanded.delete(itemId); expandedItems.value = newExpanded; return }
    newExpanded.add(itemId)
    expandedItems.value = newExpanded
    if (!itemRefundRecords.value[itemId]) await loadItemRefundRecords(itemId)
  }

  const loadItemRefundRecords = async (itemId: number) => {
    try {
      const response = await authAPI.getSellerRefundsByOrderItem(itemId)
      if (response.success && response.data) {
        const records = response.data as Array<{ id: number; refundType: string; refundStatus: string; returnStatus?: string; refundAmount: number; applyTime: string }>
        itemRefundRecords.value = { ...itemRefundRecords.value, [itemId]: records }
        if (records.length > 0 && !selectedRefundId.value) selectedRefundId.value = records[0]!.id
      } else {
        itemRefundRecords.value = { ...itemRefundRecords.value, [itemId]: [] }
      }
    } catch (error) {
      console.error('获取退款记录失败', error)
      itemRefundRecords.value = { ...itemRefundRecords.value, [itemId]: [] }
    }
  }

  const selectRefundRecord = (recordId: number) => { selectedRefundId.value = recordId }

  const closeRefundDialog = () => {
    showRefundDialog.value = false
    refundingItemList.value = []
    expandedItems.value = new Set()
    itemRefundRecords.value = {}
    selectedRefundId.value = null
  }

  const confirmSelectRefund = () => {
    if (!selectedRefundId.value) return
    showRefundDialog.value = false
    router.push({ name: 'RefundChatStep', params: { refundId: String(selectedRefundId.value) } })
  }

  const formatPrice = (price: number): string => {
    if (price == null) return '0.00'
    return price.toFixed(2)
  }

  const formatDateTime = (dateStr: string): string => {
    if (!dateStr) return '-'
    try {
      const date = new Date(dateStr)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}`
    } catch { return '-' }
  }

  onMounted(() => {
    loadOrders()
  })
</script>

<style scoped>
  @import url('@/static/css/seller/订单管理页.css');
</style>
