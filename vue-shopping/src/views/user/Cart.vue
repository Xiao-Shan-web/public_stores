<template>
  <div class="cart-container">
    <!-- 购物车导航栏 -->
    <div class="page-navbar">
      <button class="page-nav-back" @click="router.back()">
        <i class="fas fa-chevron-left"></i>
      </button>
      <div class="page-nav-title">
        <span>购物袋</span>
      </div>

    </div>

    <!-- 操作栏融入导航下方 -->
    <div class="cart-actions-bar" v-if="cartItems.length > 0">
      <div class="select-all" @click="toggleSelectAll">
        <i :class="isAllSelected ? 'fas fa-check-circle' : 'far fa-circle'"></i>
        <span>全选</span>
      </div>
      <span class="cart-items-count">共{{ cartItems.length }}件</span>
      <button class="delete-btn" @click="batchDelete">删除</button>
    </div>

    <!-- 骨架屏 -->
    <div v-if="loading" class="cart-content">
      <div class="cart-list">
        <div v-for="i in 3" :key="i" class="cart-item skeleton-product-card">
          <div class="cart-item-top">
            <div class="skeleton-image" style="width:80px;height:80px;border-radius:8px;"></div>
            <div style="flex:1;display:flex;flex-direction:column;gap:8px;">
              <div class="skeleton-line medium"></div>
              <div class="skeleton-line short"></div>
              <div class="skeleton-line short"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else-if="cartItems.length === 0" class="empty-cart">
      <i class="fas fa-shopping-cart"></i>
      <p>购物车还是空的</p>
      <RouterLink :to="{name: 'UserDashboard'}" class="btn btn-primary">去逛逛</RouterLink>
    </div>

    <div v-else class="cart-content">
      <div class="cart-list">
        <div
          v-for="item in cartItems"
          :key="item.id"
          class="cart-item"
          :class="{
            'out-of-stock': item.stock <= 0 || item.productStatus === 2,
            'deleted': item.productStatus === 2,
            'selected': selectedIds.includes(item.id)
          }"
          @click="toggleSelectItem(item)"
        >
          <div class="order-header">
            <div class="order-header-top">
              <div class="order-header-left">
                <img :src="item.sellerAvatar || sellerDefaultAvatar" class="seller-avatar" :alt="item.sellerName" />
                <span class="seller-name">{{ item.sellerName }}</span>
              </div>
            </div>
          </div>
        <div class="cart-item-top">
          <div class="item-image" @click.stop="goToProduct(item.productId)">
            <img :src="item.productImage" :alt="item.productName" />
            <div v-if="item.productStatus === 2" class="image-overlay">
              <span class="deleted-tag">已下架</span>
            </div>
          </div>
          <div class="item-info">
            <div class="item-name">{{ item.productName }}</div>
            <div class="tags-group">
              <span v-if="item.skuName" class="item-sku">{{ item.skuName }}</span>
               <span v-if="item.productStatus === 2" class="deleted-tag">商品已下架</span>
              <span v-else-if="item.stock <= 0" class="out-of-stock-tag">缺货</span>
            </div>
          </div>
          <div class="price-col">
            <div class="current-price" :class="{ 'deleted-price': item.productStatus === 2 }">¥{{ formatPrice(item.price) }}</div>
            <div v-if="item.originalPrice && item.originalPrice > item.price && item.productStatus !== 2" class="original-price">¥{{ formatPrice(item.originalPrice) }}</div>
          </div>
        </div>

          <div class="item-bottom">
            <div class="item-status">
              <span v-if="item.productStatus === 2" class="stock-text deleted-text">商品已下架</span>
              <span v-else-if="item.stock <= 0" class="stock-text">暂时缺货</span>
              <template v-else>
                <span v-if="selectedIds.includes(item.id)" class="selected-text">
                  <i class="fas fa-check"></i> 已选
                </span>
                <span v-else class="unselected-text">点此选择</span>
              </template>
            </div>

            <div class="item-quantity-tags" v-if="item.productStatus !== 2">
              <span class="qty-tag" :class="{ active: item.quantity === 1 }" @click.stop="setQuantity(item, 1)">1件</span>
              <span v-if="item.stock >= 2" class="qty-tag" :class="{ active: item.quantity === 2 }" @click.stop="setQuantity(item, 2)">2件</span>
              <span v-if="item.stock >= 3" class="qty-tag" :class="{ active: item.quantity === 3 }" @click.stop="setQuantity(item, 3)">3件</span>

              <!-- 当前数量按钮（大于3时显示） -->
              <span v-if="item.quantity >= 4" class="qty-tag active" @click.stop="showQtyPicker(item)">
                {{ item.quantity }}件
              </span>

              <!-- +更多按钮（库存大于当前显示的最大预设时显示） -->
              <span v-if="item.stock > Math.max(3, item.quantity)" class="qty-tag qty-tag-more" @click.stop="showQtyPicker(item)">
                +更多
              </span>
            </div>

            <div class="item-right-actions">
              <span
                class="delete-text-btn"
                @click.stop="removeItem(item.id)"
              >删除</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 悬浮胶囊结算条 -->
      <div class="floating-checkout-capsule" :class="{ active: selectedCount > 0 }">
        <div class="capsule-inner">
          <!-- 左侧选中信息 -->
          <div class="capsule-left">
            <div class="capsule-count-badge">
              <span class="count-num">{{ selectedCount }}</span>
            </div>
            <div class="capsule-price-info">
              <span class="capsule-total-label">合计</span>
              <span class="capsule-total-price">¥{{ formatPrice(totalAmount) }}</span>
            </div>
          </div>

          <i class="fas fa-chevron-up capsule-arrow" :class="{ expanded: showSelectedDetail }" @click="showSelectedDetail = !showSelectedDetail"></i>

          <!-- 右侧结算按钮 -->
          <button
            class="capsule-checkout-btn"
            @click="goToCheckout"
            :disabled="selectedCount === 0"
          >
            <span>去结算</span>
            <i class="fas fa-arrow-right"></i>
          </button>
        </div>

        <!-- 选中商品预览浮层 -->
        <div class="capsule-preview" v-if="showSelectedDetail && selectedCount > 0">
          <div class="preview-list">
            <div v-for="item in selectedItemsPreview" :key="item.id" class="preview-item">
              <img :src="item.productImage" class="preview-img" />
              <span class="preview-name">{{ item.productName }}</span>
              <span class="preview-qty">x{{ item.quantity }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 数量选择弹窗 -->
      <div class="qty-picker-overlay" v-if="showQtyPickerModal" @click="closeQtyPicker">
        <div class="qty-picker-content" @click.stop>
          <div class="qty-picker-header">
            <span>选择数量</span>
            <button @click="closeQtyPicker"><i class="fas fa-times"></i></button>
          </div>
          <div class="qty-picker-body">
            <div class="qty-preset-list">
              <span
                v-for="n in qtyPresets"
                :key="n"
                class="qty-preset-item"
                :class="{ active: pickerValue === n }"
                @click="pickerValue = n"
              >{{ n }}件</span>
            </div>
            <div class="qty-custom-row">
              <span>自定义数量</span>
              <div class="qty-input-group">
                <button @click="pickerValue = Math.max(1, pickerValue - 1)">-</button>
                <input type="number" v-model.number="pickerValue" min="1" :max="pickerMaxStock" />
                <button @click="pickerValue = Math.min(pickerMaxStock, pickerValue + 1)">+</button>
              </div>
            </div>
            <div class="qty-stock-info">库存 {{ pickerMaxStock }} 件</div>
          </div>
          <div class="qty-picker-footer">
            <button class="btn-confirm-qty" @click="confirmQtyPicker">确定</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'Cart' })
  // ========== 依赖导入 ==========
  import { ref, computed, onMounted } from 'vue'
  import { useRouter } from 'vue-router'
  import { authAPI } from '@/api/authAPI'
  import Message from '@/utils/message'
  import { useAuthStore } from '@/stores/auth'
  import sellerDefaultAvatar from '@/static/images/seller-avatar.jpg'

  // 路由实例
  const router = useRouter()

  // ========== 全局状态 ==========
  const authStore = useAuthStore()

  // ========== 类型定义 ==========
  /** 购物车商品项 */
  interface CartItem {
    id: number           // 购物车项ID
    userId: number       // 用户ID
    productId: number    // 商品ID
    skuId?: number       // SKU ID（可选）
    quantity: number     // 购买数量
    productName: string  // 商品名称
    skuName?: string     // SKU 规格名称
    brand: string        // 品牌
    price: number        // 单价
    originalPrice?: number  // 原价
    productImage: string // 商品图片
    stock: number        // 库存数量
    productStatus?: number // 商品状态（0-下架，1-上架，2-已删除）
    sellerName?: string  // 卖家名称
    sellerAvatar?: string // 卖家头像
  }

  // ========== 响应式数据 ==========
  /** 加载状态 */
  const loading = ref(true)
  /** 购物车列表数据 */
  const cartItems = ref<CartItem[]>([])
  /** 已选中的购物车项ID列表 */
  const selectedIds = ref<number[]>([])


  // 数量选择弹窗相关
  const showQtyPickerModal = ref(false)
  const pickerValue = ref(1)
  const pickerMaxStock = ref(99)
  const currentPickerItem = ref<CartItem | null>(null)
  const qtyPresets = [1, 2, 3, 5, 10, 20]

  // 选中商品预览展开/收起
  const showSelectedDetail = ref(false)

  // 选中商品的简要预览
  const selectedItemsPreview = computed(() => {
    const items = cartItems.value.filter(item => selectedIds.value.includes(item.id))
    return items
  })

  // ========== 计算属性 ==========

  /**
     * 可选的购物车项（库存大于0且商品未被删除的商品）
     */
    const selectableItems = computed(() =>
      cartItems.value.filter(item => item.stock > 0 && item.productStatus !== 2)
    )

    /**
     * 不可购买商品数量（库存为0或已删除的商品）
     */
    const outOfStockCount = computed(() =>
      cartItems.value.filter(item => item.stock <= 0 || item.productStatus === 2).length
    )

  /**
   * 是否全选（只针对可选商品）
   */
  const isAllSelected = computed(() => {
    // 如果没有可选商品，返回false
    if (selectableItems.value.length === 0) return false
    // 获取已选中的可选商品ID集合
    const selectedSelectableIds = selectedIds.value.filter(id =>
      selectableItems.value.some(item => item.id === id)
    )
    // 判断是否全选
    return selectedSelectableIds.length === selectableItems.value.length
  })

  /** 选中商品总件数（只统计可选商品） */
  const selectedCount = computed(() =>
    cartItems.value
      .filter(item => selectedIds.value.includes(item.id) && item.stock > 0)
      .reduce((sum, item) => sum + item.quantity, 0)
  )

  /** 选中商品总金额（只统计可选商品） */
  const totalAmount = computed(() =>
    cartItems.value
      .filter(item => selectedIds.value.includes(item.id) && item.stock > 0)
      .reduce((sum, item) => sum + item.price * item.quantity, 0)
  )

  // ========== 业务方法 ==========

  /**
   * 获取购物车列表
   */
  const loadCartList = async () => {
    loading.value = true
    try {
      const response = await authAPI.getCartList()
      if (response.success && response.data?.records) {
        cartItems.value = response.data.records
        // 加载完成后，清空已选中的缺货或已删除商品ID
        selectedIds.value = selectedIds.value.filter(id => {
          const item = cartItems.value.find(i => i.id === id)
          return item && item.stock > 0 && item.productStatus !== 2
        })
      } else {
        throw new Error(response.message || '加载购物车失败')
      }
    } catch (error: any) {
      Message.error(error.message || '加载购物车失败')
    } finally {
      loading.value = false
    }
  }

  /**
     * 切换单个商品选中状态
     * @param item - 目标购物车项
     */
    const toggleSelectItem = (item: CartItem) => {
        if (item.stock <= 0 || item.productStatus === 2) return

        const index = selectedIds.value.indexOf(item.id)
        if (index > -1) {
            selectedIds.value.splice(index, 1)
        } else {
            selectedIds.value.push(item.id)
        }
    }

  /**
     * 全选/取消全选
     * 只操作可选商品（库存大于0且未被删除的商品），缺货或已删除商品不会被选中
     */
    const toggleSelectAll = () => {
        if (isAllSelected.value) {
            // 取消全选：清空所有选中
            selectedIds.value = []
        } else {
            // 全选：只选中可选商品的ID（缺货或已删除商品不选中）
            const allSelectableIds = selectableItems.value.map(item => item.id)
            selectedIds.value = [...allSelectableIds]

            // 如果有缺货或已删除商品，给出提示
            if (outOfStockCount.value > 0) {
                const deletedCount = cartItems.value.filter(item => item.productStatus === 2).length
                const stockOutCount = cartItems.value.filter(item => item.stock <= 0 && item.productStatus !== 2).length
                let msg = `已选中${allSelectableIds.length}件商品`
                if (deletedCount > 0) msg += `，${deletedCount}件商品已下架不可选`
                if (stockOutCount > 0) msg += `，${stockOutCount}件商品缺货不可选`
                Message.info(msg)
            }
        }
    }

  /**
   * 更新商品数量（调用API）
   * @param item - 目标购物车项
   */
  const updateQuantity = async (item: CartItem) => {
    // 边界校验
    if (item.quantity < 1) {
      item.quantity = 1
      return
    }
    if (item.quantity > item.stock) {
      item.quantity = item.stock
      Message.error(`库存不足，最多可购买${item.stock}件`)
      return
    }

    try {
      const response = await authAPI.updateCartItem({
        cartItemId: item.id,
        quantity: item.quantity
      })
      if (!response.success) {
        throw new Error(response.message || '更新失败')
      }
    } catch (error: any) {
      Message.error(error.message || '更新失败')
      await loadCartList() // 回滚数据
    }
  }

  /** 减少商品数量 */
  const decreaseQuantity = (item: CartItem) => {
    if (item.quantity > 1) {
      item.quantity--
      updateQuantity(item)
    }
  }

  /** 增加商品数量 */
  const increaseQuantity = (item: CartItem) => {
    if (item.quantity < item.stock) {
      item.quantity++
      updateQuantity(item)
    } else {
      Message.error(`库存不足，最多可购买${item.stock}件`)
    }
  }

  /**
   * 直接设置数量
   * @param item - 目标购物车项
   * @param qty - 目标数量
   */
  const setQuantity = (item: CartItem, qty: number) => {
    if (qty > item.stock) {
      Message.error(`库存不足，最多${item.stock}件`)
      return
    }
    item.quantity = qty
    updateQuantity(item)
  }

  /**
   * 弹出数量选择器（超过3件时）
   * @param item - 目标购物车项
   */
  const showQtyPicker = (item: CartItem) => {
    currentPickerItem.value = item
    pickerMaxStock.value = item.stock
    pickerValue.value = Math.min(item.quantity, item.stock)
    showQtyPickerModal.value = true
  }

  /**
   * 关闭数量选择器
   */
  const closeQtyPicker = () => {
    showQtyPickerModal.value = false
    currentPickerItem.value = null
  }

  /**
   * 确认数量选择
   */
  const confirmQtyPicker = () => {
    if (currentPickerItem.value && pickerValue.value >= 1) {
      setQuantity(currentPickerItem.value, pickerValue.value)
    }
    closeQtyPicker()
  }

  /**
   * 删除单个购物车商品（带二次确认）
   * @param cartItemId - 购物车项ID
   */
  const removeItem = async (cartItemId: number) => {
    try {
      await Message.confirm('确定要删除该商品吗？', '删除确认')
      const response = await authAPI.deleteCartItem(cartItemId)
      if (response.success) {
        Message.success('删除成功')
        // 从已选列表中移除
        selectedIds.value = selectedIds.value.filter(id => id !== cartItemId)
        await loadCartList()
      } else {
        throw new Error(response.message || '删除失败')
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        Message.error(error.message || '删除失败')
      }
    }
  }

  /**
   * 批量删除选中的商品（带二次确认）
   */
  const batchDelete = async () => {
    if (selectedIds.value.length === 0) return

    try {
      await Message.confirm(`确定要删除选中的 ${selectedIds.value.length} 个商品吗？`, '批量删除确认')
      const response = await authAPI.batchDeleteCartItems(selectedIds.value)
      if (response.success) {
        Message.success(`成功删除${selectedIds.value.length}个商品`)
        selectedIds.value = []
        await loadCartList()
      } else {
        throw new Error(response.message || '批量删除失败')
      }
    } catch (error: any) {
      if (error !== 'cancel') {
        Message.error(error.message || '批量删除失败')
      }
    }
  }

  /**
   * 跳转至商品详情页
   * @param productId - 商品ID
   */
  const goToProduct = (productId: number) => {
    router.push({ name: 'ProductDetail', params: { productId } })
  }

  /**
   * 去结算：校验选中商品并跳转至订单确认页
   */
  const goToCheckout = () => {
    if (selectedCount.value === 0) {
      Message.error('请选择要结算的商品')
      return
    }
    router.push({
      name: 'Checkout',
      query: {
        source: 'cart',
        cartItemIds: selectedIds.value.join(',')
      }
    })
  }

  // ========== 工具函数 ==========
  /** 格式化金额（保留两位小数） */
  const formatPrice = (price: number): string => {
    if (price == null || isNaN(price)) return '0.00'
    return price.toFixed(2)
  }

  // ========== 生命周期 ==========
  onMounted(() => {
  if (!authStore.validateUserPermission()) return
  loadCartList()
})
</script>

<style scoped>
@import url('@/static/css/user/购物车.css');
</style>
