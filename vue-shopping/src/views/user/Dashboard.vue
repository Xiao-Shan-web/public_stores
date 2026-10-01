<template>
  <div class="outer-container" @touchstart.passive="onTouchStart" @touchmove="onTouchMove" @touchend="onTouchEnd">
    <!-- ========== 下拉刷新指示器 ========== -->
    <div class="refresh-indicator" :style="{ height: pullDistance + 'px', opacity: pullDistance / 60 }">
      <div class="refresh-content" v-if="pullDistance > 0">
        <div class="mini-ring" v-if="!isRefreshing"></div>
        <i v-if="isRefreshing" class="fas fa-sparkles brand-icon-pulse"></i>
        <span>{{ isRefreshing ? '云杉购·刷新中' : (pullDistance > 60 ? '释放刷新' : '下拉刷新') }}</span>
      </div>
    </div>

    <!-- ========== 固定导航栏（搜索+分类） ========== -->
    <div class="fixed-header">
      <!-- 搜索栏 -->
      <div class="breathe-nav">
        <div class="nav-left">
          <div class="nav-logo">
            <i class="fas fa-store-alt"></i>
            <span class="logo-text">云杉购</span>
          </div>
        </div>
        <div class="nav-center">
          <div class="search-input-wrap" @click="router.push({ name: 'SearchDefault', query: { autofocus: 'true', placeholder: searchPlaceholderText } })">
            <i class="fas fa-search search-input-icon"></i>
            <div class="search-placeholder-wrapper">
              <transition name="slide-up-down" mode="out-in">
                <span class="search-placeholder-text" :key="searchPlaceholderText">
                  {{ searchPlaceholderText }}
                </span>
              </transition>
            </div>
          </div>
        </div>
      </div>

      <!-- 一级分类Tab -->
      <div class="category-tabs-scroll" ref="categoryTabsRef">
        <div class="category-tab" :class="{ active: activeLevel1Id === null }" @click="selectLevel1Category(null)">
          全部
        </div>
        <div
          v-for="cat in level1Categories"
          :key="cat.id"
          class="category-tab"
          :class="{ active: activeLevel1Id === cat.id }"
          @click="selectLevel1Category(cat.id)"
        >
          {{ cat.name }}
        </div>
        <div class="tab-underline" ref="tabUnderlineRef"></div>
      </div>
    </div>

    <!-- ========== 内容区域 ========== -->
    <div class="main-content">
      <!-- ========== 商家入驻横幅 ========== -->
      <div class="merchant-banner" v-if="!authStore.hasRole('ROLE_SELLER') && !authStore.hasRole('ROLE_ADMIN')">
        <div class="merchant-container">
          <div class="merchant-content">
            <div class="merchant-info">
              <h3 class="merchant-title">加入云杉购商城，开启电商之旅</h3>
              <p class="merchant-desc">
                <span>0元入驻</span>
                <span>海量流量</span>
                <span>专业扶持</span>
              </p>
            </div>
            <button class="merchant-btn" @click="goToMerchantApply">
              <i class="fas fa-store"></i>
              <span>立即入驻</span>
              <i class="fas fa-arrow-right"></i>
            </button>
          </div>
        </div>
      </div>

      <!-- ========== 二级分类标签（选中一级分类时显示） ========== -->
      <div v-if="activeLevel1Id !== null && level2Categories.length > 0" class="level2-category-section">
        <div class="level2-category-tabs">
          <div
            v-for="cat in level2Categories"
            :key="cat.id"
            class="level2-category-tab"
            :class="{ active: activeLevel2Id === cat.id }"
            @click="selectLevel2Category(cat.id)"
          >
            {{ cat.name }}
          </div>
        </div>
      </div>

    <!-- ========== 商品展示区 ========== -->
    <div class="products-section">
      <div class="products-container">
        <!-- 商品网格 + 骨架屏 -->
        <div class="products-grid" v-if="products.length > 0 || loading">
          <!-- 加载骨架屏（仅首次加载且无数据时显示） -->
          <template v-if="loading && products.length === 0">
            <div v-for="i in 6" :key="'skeleton-' + i" class="product-card skeleton-card">
              <div class="product-image">
                <div class="image-skeleton"></div>
              </div>
              <div class="product-info">
                <div class="skeleton-line skeleton-title"></div>
                <div class="skeleton-line skeleton-price"></div>
              </div>
            </div>
          </template>

          <!-- 真实商品列表 -->
          <div v-for="product in products" :key="product.id" class="product-card" @click="viewProductDetail(product.id)">
            <div class="product-image">
              <span v-if="product.badge" class="product-badge" :style="{ backgroundColor: product.badgeColor }">{{ product.badge }}</span>
              <span v-if="product.stock <= 0" class="out-of-stock-tag">缺货</span>
              <div class="image-skeleton" v-show="!product.imageLoaded"></div>
              <img
                :src="product.productImages?.[0]?.image || product.images"
                :alt="product.name"
                :class="{ 'img-loaded': product.imageLoaded }"
                @load="product.imageLoaded = true"
                @error="product.imageLoaded = true"
                v-show="product.imageLoaded"
              >
              <div class="product-actions">
                <button class="quick-view" @click.stop="quickView(product)"><i class="fas fa-eye"></i></button>
                <button class="add-to-cart" @click.stop="addToCart(product)" :disabled="product.stock <= 0">
                  <i class="fas fa-shopping-cart"></i>
                </button>
              </div>
            </div>
            <div class="product-info">
              <div class="product-title">{{ product.name }}</div>
              <div class="price-row">
                <span class="current-price">¥{{ formatPrice(product.price) }}</span>
                <span v-if="((product.originalPrice ?? product.original_price) !== undefined) && ((product.originalPrice ?? product.original_price) as number) > product.price"
                  class="original-price">¥{{ formatPrice((product.originalPrice ?? product.original_price) as number) }}</span>
              </div>
              <div class="product-sales">已售 {{ product.salesCount || 0 }} 件</div>
            </div>
          </div>
        </div>

        <!-- 空状态（非加载中且无数据） -->
        <div v-else-if="!loading && products.length === 0" class="empty-products">
          <i class="fas fa-box-open"></i>
          <p>暂无商品</p>
        </div>
      </div>

      <!-- 加载更多提示 - 移到 products-container 外面，固定在商品区域下方 -->
      <div class="load-more-wrapper">
        <div v-if="loadingMore" class="loading-more-bar">
          <i class="fas fa-spinner fa-spin"></i>
          <span>加载中...</span>
        </div>
        <div v-else-if="!hasMore && products.length > 0" class="no-more-bar">
          — 已经到底了 —
        </div>
      </div>
    </div>
    </div>

    <!-- ========== 快速查看模态框 ========== -->
    <div v-if="showQuickView" class="modal-overlay" @click="closeQuickView">
      <div class="modal-content" @click.stop>
        <button class="modal-close" @click="closeQuickView"><i class="fas fa-times"></i></button>
        <div class="quick-view-content" v-if="selectedProduct">
          <div class="quick-view-image">
            <img :src="selectedProduct.images" :alt="selectedProduct.name">
          </div>
          <div class="quick-view-info">
            <h3>{{ selectedProduct.name }}</h3>
            <div class="quick-view-price">
              <span class="current-price">¥{{ formatPrice(selectedProduct.price) }}</span>
              <span v-if="((selectedProduct.originalPrice ?? selectedProduct.original_price) !== undefined) && ((selectedProduct.originalPrice ?? selectedProduct.original_price) as number) > selectedProduct.price" class="original-price">
                ¥{{ formatPrice((selectedProduct.originalPrice ?? selectedProduct.original_price) as number) }}
              </span>
              <span v-if="((selectedProduct.originalPrice ?? selectedProduct.original_price) !== undefined) && ((selectedProduct.originalPrice ?? selectedProduct.original_price) as number) > selectedProduct.price" class="discount-badge">
                {{ getDiscountPercent(selectedProduct.price, (selectedProduct.originalPrice ?? selectedProduct.original_price) as number) }}% OFF
              </span>
            </div>
            <div class="quick-view-stats">
              <span>销量: {{ selectedProduct.salesCount || 0 }}件</span>
              <span>库存: {{ selectedProduct.stock || 0 }}件</span>
            </div>
            <p class="quick-view-desc">{{ selectedProduct.description || '暂无商品描述' }}</p>
            <button class="buy-now-btn" @click="buyNow(selectedProduct)">立即购买</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 回到顶部按钮 -->
    <div class="back-to-top" :class="{ show: showBackTop }" @click="scrollToTop">
      <i class="fas fa-arrow-up"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'UserDashboard' })

import { ref, computed, onMounted, onUnmounted, onActivated, onDeactivated, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authAPI } from '@/api/authAPI'
import Message from '@/utils/message'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'

// 搜索栏 placeholder 轮播
const browseHistory = ref<string[]>([])
const searchPlaceholderText = ref('')
let searchPlaceholderTimer: ReturnType<typeof setInterval> | null = null
let searchPlaceholderIndex = 0

const loadSearchBrowseHistory = async () => {
  const local = JSON.parse(localStorage.getItem('browseHistory') || '[]')
  const names = local.slice(0, 5).map((h: any) => h.productName || h.name)
  browseHistory.value = names
  if (names.length > 0) {
    searchPlaceholderText.value = names[0]
  }

  try {
    const browseRes = await authAPI.getBrowseHistory()
    if (browseRes.success && browseRes.data?.records?.length > 0) {
      const serverNames = browseRes.data.records.map((h: any) => h.productName)
      browseHistory.value = serverNames.slice(0, 5)
      searchPlaceholderText.value = serverNames[0]
      localStorage.setItem('browseHistory', JSON.stringify(browseRes.data.records))
    } else {
      const recommendRes = await authAPI.getRecommendProducts()
      if (recommendRes.success && recommendRes.data?.length > 0) {
        const recommendNames = recommendRes.data.map((p: any) => p.name)
        browseHistory.value = recommendNames.slice(0, 5)
        searchPlaceholderText.value = recommendNames[0]
      }
    }
  } catch {
    try {
      const recommendRes = await authAPI.getRecommendProducts()
      if (recommendRes.success && recommendRes.data?.length > 0) {
        const recommendNames = recommendRes.data.map((p: any) => p.name)
        browseHistory.value = recommendNames.slice(0, 5)
        searchPlaceholderText.value = recommendNames[0]
      }
    } catch {}
  }
}

const startSearchPlaceholderRotation = () => {
  searchPlaceholderTimer = setInterval(() => {
    if (browseHistory.value.length > 0) {
      searchPlaceholderIndex = (searchPlaceholderIndex + 1) % browseHistory.value.length
      searchPlaceholderText.value = browseHistory.value[searchPlaceholderIndex] || ''
    }
  }, 5000)
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { isLoggedIn, isSeller, isAdmin } = storeToRefs(authStore)


// ==================== 类型定义 ====================

interface Category {
  id: number
  name: string
  parentId: number | null
  icon?: string
  isActive: boolean
}

interface Product {
  id: number
  name: string
  brand: string
  price: number
  originalPrice?: number
  original_price?: number
  stock: number
  images?: string
  productImages?: Array<{ image: string }>
  salesCount?: number
  description?: string
  badge?: string
  badgeColor?: string
  imageLoaded?: boolean   // 新增：图片是否加载完成
}

interface Banner {
  id: number
  image: string
  title: string
  subtitle: string
  link: string
}

// ==================== 用户相关状态 ====================

const cartCount = ref(0)

// ==================== 分类相关状态 ====================

const allCategories = ref<Category[]>([])
const level1Categories = ref<Category[]>([])
const level2Categories = ref<Category[]>([])
const activeLevel1Id = ref<number | null>(null)
const activeLevel2Id = ref<number | null>(null)

// ==================== 商品相关状态 ====================

const products = ref<Product[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const goToSearch = () => {
  if (searchKeyword.value.trim()) {
    router.push({ name: 'SearchResult', query: { keyword: searchKeyword.value.trim() } })
  } else {
    router.push({ name: 'SearchDefault' })
  }
}

// ==================== UI 状态 ====================

const showQuickView = ref(false)
const selectedProduct = ref<Product | null>(null)
const showBackTop = ref(false)
const showFixedHeader = ref(false)

// 下拉刷新相关
const pullDistance = ref(0)
const isRefreshing = ref(false)
const touchStartY = ref(0)
const isPulling = ref(false)

// Tab 相关 refs
const categoryTabsRef = ref<HTMLElement | null>(null)
const tabUnderlineRef = ref<HTMLElement | null>(null)

const scrollToCategorySection = () => {}

// ==================== 分类图标颜色 ====================

const catBgColors = [
  '#FF6B6B', '#4a6491', '#10B981', '#F59E0B',
  '#8B5CF6', '#EC4899', '#06B6D4', '#F97316'
]

// 获取分类图标
const getCatIcon = (name: string): string => {
  const iconMap: Record<string, string> = {
    '手机数码': 'fas fa-mobile-alt',
    '电脑办公': 'fas fa-laptop',
    '家用电器': 'fas fa-tv',
    '服饰鞋包': 'fas fa-tshirt',
    '美妆护肤': 'fas fa-spa',
    '食品生鲜': 'fas fa-apple-alt',
    '母婴玩具': 'fas fa-baby',
    '家居家装': 'fas fa-home',
    '运动户外': 'fas fa-running',
    '图书文娱': 'fas fa-book',
    '蛋糕烘焙': 'fas fa-birthday-cake',
    '宠物生活': 'fas fa-paw',
    '医药健康': 'fas fa-heartbeat',
    '汽车用品': 'fas fa-car',
    '花卉绿植': 'fas fa-seedling',
    '礼品鲜花': 'fas fa-gift',
    '酒水冲调': 'fas fa-wine-glass-alt',
    '农资园艺': 'fas fa-leaf',
    '二手闲置': 'fas fa-recycle',
    '钟表珠宝': 'fas fa-gem',
  }
  return iconMap[name] || 'fas fa-tag'
}

// ==================== 计算属性 ====================

// 取前8个一级分类作为快捷入口
const topCategories = computed(() => level1Categories.value.slice(0, 8))

const currentCategoryName = computed(() => {
  if (activeLevel2Id.value) {
    const category = level2Categories.value.find(c => c.id === activeLevel2Id.value)
    return category ? category.name : '全部商品'
  }
  if (activeLevel1Id.value) {
    const category = level1Categories.value.find(c => c.id === activeLevel1Id.value)
    return category ? category.name : '全部商品'
  }
  return '全部商品'
})



// ==================== 用户相关函数 ====================

const loadCartCount = async () => {
  if (!isLoggedIn.value) return

  try {
    const response = await authAPI.getCartCount()
    if (response.success) {
      cartCount.value = response.data || 0
    }
  } catch (error) {
    Message.error('加载购物车数量失败')
    cartCount.value = 0
  }
}

// ==================== 分类相关函数 ====================

let loadingCategories = false
const loadCategories = async () => {
  if (loadingCategories || allCategories.value.length > 0) return
  loadingCategories = true
  try {
    const response = await authAPI.getAllCategories()
    if (response.success && response.data?.categories) {
      allCategories.value = response.data.categories
      level1Categories.value = allCategories.value.filter(
        cat => !cat.parentId && cat.isActive
      )
    }
  } catch (error) {
    Message.error('加载分类失败')
  } finally {
    loadingCategories = false
  }
}

const loadLevel2Categories = () => {
  if (activeLevel1Id.value) {
    level2Categories.value = allCategories.value.filter(
      cat => cat.parentId === activeLevel1Id.value && cat.isActive
    )
  } else {
    level2Categories.value = []
  }
}

const selectLevel1Category = (categoryId: number | null) => {
  activeLevel1Id.value = categoryId
  activeLevel2Id.value = null
  loadLevel2Categories()
  currentPage.value = 1
  hasMore.value = true
  products.value = []
  loadProducts()
  updateTabUnderline()
}

const selectLevel2Category = (categoryId: number | null) => {
  activeLevel2Id.value = categoryId
  currentPage.value = 1
  hasMore.value = true
  products.value = []   // 清空旧商品，触发骨架屏
  loadProducts()
}

// ==================== 商品相关函数 ====================

const loadProducts = async () => {
  // 分类未加载时不请求
  if (level1Categories.value.length === 0 && activeLevel1Id.value !== null) {
    return
  }

  loading.value = true

  try {
    const params: any = {
      page: currentPage.value,
      pageSize: pageSize.value
    }

    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }

    if (activeLevel2Id.value) {
      params.level2CategoryId = activeLevel2Id.value
    } else if (activeLevel1Id.value) {
      params.level1CategoryId = activeLevel1Id.value
    }

    const response = await authAPI.getProducts(params)

    if (response.success) {
      const newProducts = response.data?.records || []
      const productsWithState = newProducts.map((p: Product) => ({
        ...p,
        imageLoaded: false
      }))

      if (currentPage.value === 1) {
        products.value = productsWithState
      } else {
        products.value.push(...productsWithState)
      }
      hasMore.value = newProducts.length >= pageSize.value
    } else {
      throw new Error(response.message || '加载商品失败')
    }
  } catch (error: any) {
    Message.error('加载商品失败')
    if (currentPage.value === 1) {
      products.value = []
    }
  } finally {
    loading.value = false
  }
}

const handleScroll = () => {
  const scrollTop = window.scrollY

  showFixedHeader.value = scrollTop > 50

  showBackTop.value = scrollTop > 300

  // 加载更多
  if (loadingMore.value || !hasMore.value || loading.value) return
  const { scrollHeight, clientHeight } = document.documentElement
  if (scrollTop + clientHeight >= scrollHeight - 150) {
    loadMore()
  }
}

const updateTabUnderline = () => {
  nextTick(() => {
    const tabs = categoryTabsRef.value?.querySelectorAll('.category-tab')
    const activeTab = categoryTabsRef.value?.querySelector('.category-tab.active')
    const underline = tabUnderlineRef.value

    if (!activeTab || !underline) return

    const rect = activeTab.getBoundingClientRect()
    const containerRect = categoryTabsRef.value?.getBoundingClientRect()

    if (containerRect) {
      underline.style.left = `${rect.left - containerRect.left}px`
      underline.style.width = `${rect.width}px`
    }
  })
}

const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const loadMore = async () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  currentPage.value++
  await loadProducts()
  loadingMore.value = false
}

const performSearch = () => {
  if (searchKeyword.value.trim()) {
    currentPage.value = 1
    loadProducts()
  }
}

// ==================== UI 交互函数 ====================

const viewProductDetail = (productId: number) => {
  router.push({
    name: 'ProductDetail',
    params: { productId }
  })
}

const quickView = (product: Product) => {
  selectedProduct.value = product
  showQuickView.value = true
}

const closeQuickView = () => {
  showQuickView.value = false
  selectedProduct.value = null
}

const addToCart = async (product: Product) => {
  if (!isLoggedIn.value) {
    Message.error('请先登录后再添加')
    router.push({ name: 'Login' })
    return
  }

  try {
    const response = await authAPI.addToCart({ productId: product.id, quantity: 1 })
    if (response.success) {
      Message.success('已添加到购物车')
      await loadCartCount()
    }
  } catch (error) {
    Message.error('添加失败')
  }
}

const buyNow = (product: Product) => {
  router.push({
    name: 'Checkout',
    query: {
      'productId': `${product.id}`,
      'quantity': 1
    }
  })
}

const goToMerchantApply = () => {
  router.push({ name: 'MerchantApply' })
}

// ==================== 下拉刷新函数 ====================

const onTouchStart = (e: TouchEvent) => {
  if (window.scrollY === 0) {
    touchStartY.value = e.touches[0]!.clientY
    isPulling.value = true
  }
}

const onTouchMove = (e: TouchEvent) => {
  if (!isPulling.value || isRefreshing.value) return
  const currentY = e.touches[0]!.clientY
  const diff = currentY - touchStartY.value
  if (diff > 0) {
    pullDistance.value = Math.min(diff * 0.5, 80)
  }
}

const onTouchEnd = async () => {
  if (!isPulling.value) return
  isPulling.value = false

  if (pullDistance.value > 30 && !isRefreshing.value) {
    isRefreshing.value = true
    pullDistance.value = 50  // 保持在刷新状态
    await handleRefresh()
    isRefreshing.value = false
  }
  pullDistance.value = 0
}

// ==================== 刷新函数 ====================

const handleRefresh = async () => {
  currentPage.value = 1
  hasMore.value = true
  products.value = []        // 先清空旧数据，触发骨架屏
  loading.value = true
  await loadProducts()
  await loadCartCount()
  loading.value = false
  Message.success('已刷新')
}

// ==================== 工具函数 ====================

const formatPrice = (price: number | null | undefined): string => {
  if (!price && price !== 0) return '0.00'
  return Number(price).toFixed(2)
}

const getDiscountPercent = (price: number | null | undefined, originalPrice: number | null | undefined): number => {
  if (!originalPrice || originalPrice <= (price || 0)) return 0
  return Math.round((1 - (price || 0) / originalPrice) * 100)
}

// ==================== 生命周期 ====================

onMounted(() => {
  if (!authStore.validateAccountStatus()) {
    return
  }

  if (isLoggedIn.value) {
    loadCartCount()
  }

  // 先加载分类，完成后再加载商品
  loadCategories().then(() => {
    const queryKeyword = route.query.keyword as string
    if (queryKeyword) {
      searchKeyword.value = queryKeyword
    }
    loadProducts()
    updateTabUnderline()
  })

  loadSearchBrowseHistory()
  startSearchPlaceholderRotation()
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  if (searchPlaceholderTimer) clearInterval(searchPlaceholderTimer)
})

onActivated(() => {
  window.addEventListener('scroll', handleScroll)
})

onDeactivated(() => {
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
@import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css');
@import url('@/static/css/user/用户首页.css');
</style>
