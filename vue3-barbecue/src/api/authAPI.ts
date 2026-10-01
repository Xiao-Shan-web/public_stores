import http from "@/utils/axios-config"

// ==================== 类型定义 ====================
interface ApiResponse<T = any> {
  success: boolean
  data: T
  message?: string
}

// ==================== 内部请求包装 ====================
function request<T = any>(
  method: 'get' | 'post' | 'put' | 'delete',
  url: string,
  dataOrConfig?: any,
  config?: any
): Promise<ApiResponse<T>> {
  if (method === 'get' || method === 'delete') {
    return http[method](url, dataOrConfig) as unknown as Promise<ApiResponse<T>>
  } else {
    return http[method](url, dataOrConfig, config) as unknown as Promise<ApiResponse<T>>
  }
}

const api = {
  get: <T = any>(url: string, config?: any) => request<T>('get', url, config),
  post: <T = any>(url: string, data?: any, config?: any) => request<T>('post', url, data, config),
  put: <T = any>(url: string, data?: any, config?: any) => request<T>('put', url, data, config),
  delete: <T = any>(url: string, config?: any) => request<T>('delete', url, config),
}

// ==================== API 定义 ====================
export const authAPI = {

  // ==================== 用户相关 ====================

  /**
   * 获取用户ID（登录/注册）
   * @param deviceId 设备标识符
   */
  getUserId: (deviceId: string) => {
    return api.get(`/auth/${deviceId}`)
  },

  /**
   * 绑定手机号
   * @param userId 用户ID
   * @param phone 手机号
   */
  bindPhone: (userId: number, phone: string) => {
    return api.put(`/user/${userId}/phone`, { phone })
  },

  /**
   * 通过手机号查询用户ID（找回账号）
   * @param phone 手机号
   */
  findUserIdByPhone: (phone: string) => {
    return api.get(`/user/findByPhone`, { params: { phone } })
  },

  /**
   * 获取用户信息
   * @param userId 用户ID
   */
  getUserInfo: (userId: number) => {
    return api.get(`/user/${userId}`)
  },

  // ==================== 订单相关 ====================

  /**
   * 创建订单
   * @param userId 用户ID
   * @param data 订单数据
   */
  createOrder: (userId: number, data: any) => {
    return api.post(`/orders/${userId}`, data)
  },

  /**
   * 获取订单详情
   * @param orderNumber 订单号
   */
  getOrderDetail: (orderNumber: string) => {
    return api.get(`/orders/detail/${orderNumber}`)
  },

  /**
   * 获取用户订单列表（分页）
   * @param userId   用户ID
   * @param page     页码，默认1
   * @param pageSize 每页数量，默认20
   * @param status   订单状态（可选），空字符串表示全部
   */
  getUserOrders: (userId: number, page: number = 1, pageSize: number = 20, status: string = '') => {
    return api.get(`/orders/user/${userId}`, {
      params: { page, pageSize, status }
    })
  },

  /**
   * 取消订单
   * @param orderNumber 订单号
   */
  cancelOrder: (orderNumber: string) => {
    return api.put(`/orders/cancel/${orderNumber}`)
  },

  /**
   * 申请退款
   * @param params 退款参数
   */
  refundOrder: (params: { orderNumber: string; refundAmount: number; refundReason: string }) => {
    return api.post('/orders/refund', params)
  },

  /**
   * 确认收货（用户用）
   * @param orderNumber 订单号
   */
  confirmReceipt: (orderNumber: string) => {
    return api.put(`/orders/confirm/${orderNumber}`)
  },

  /**
   * 发货（商家用）- 外卖配送
   * @param orderNumber 订单号
   */
  shipOrder: (orderNumber: string) => {
    return api.put(`/orders/ship/${orderNumber}`)
  },

  /**
   * 核销/完成订单（商家用）- 到店用餐/打包自取
   * @param orderNumber 订单号
   */
  completeOrder: (orderNumber: string) => {
    return api.put(`/orders/complete/${orderNumber}`)
  },

  // ==================== 支付相关 ====================

  /**
   * 支付订单
   * @param params 支付参数
   */
  paymentOrder: (params: { orderNumber: string; amount: number; paymentMethod: string }) => {
    return api.post('/order/payment', params)
  },

  /**
   * 查询支付结果
   * @param orderNumber 订单号
   */
  queryPaymentResult: (orderNumber: string) => {
    return api.get(`/payments/result/${orderNumber}`)
  },

  // ==================== 商家相关 ====================

  /**
   * 获取商家信息
   */
  getSellerProfile: () => {
    return api.get('/seller/profile')
  },

  /**
   * 更新商家信息
   * @param data 商家信息
   */
  updateSellerProfile: (data: any) => {
    return api.put('/seller/profile', data)
  },

  /**
   * 更新商家头像
   * @param data FormData 包含头像文件
   */
  updateSellerAvatar: (data: FormData) => {
    return api.post("/seller/avatar", data)
  },

  /**
   * 获取商家订单列表
   * @param page 页码
   * @param pageSize 每页数量
   * @param status 订单状态筛选
   */
  getSellerOrders: (page: number = 1, pageSize: number = 20, status?: string) => {
    const params: any = { page, pageSize }
    if (status) params.status = status
    return api.get('/seller/orders', { params })
  },

  /**
   * 商家处理退款
   */
  handleRefund(params: {
    orderNumber: string
    result: 'SUCCESS' | 'FAIL'
    failReason?: string
  }) {
    return api.put('/orders/refund/handle', params)
  },

  // ==================== 商品相关 ====================

  /**
   * 添加商品
   * @param data FormData 包含商品信息和图片
   */
  addProduct: (data: FormData) => {
    return api.post("/seller/products", data)
  },

  /**
   * 删除商品
   * @param productId 商品ID
   */
  deleteProduct: (productId: string | number) => {
    return api.delete(`/seller/products/${productId}`)
  },

  /**
   * 修改商品
   * @param productId 商品ID
   * @param data 商品数据
   */
  updateProduct: (productId: number | string, data: any) => {
    return api.put(`/seller/products/${productId}`, data)
  },

  /**
   * 获取所有商品
   */
  getAllProducts: () => {
    return api.get("/seller/products")
  },

  // =========== 消息接口 ============

  /**
   * 用户发送客服消息
   */
  userSendChatMessage: (data: { userId: number; content: string }) => {
      return api.post('/chat/user/send', data)
  },

  /**
   * 商家回复消息
   */
  sellerReplyChatMessage: (data: { userId: number; content: string }) => {
      return api.post('/chat/seller/reply', data)
  },

  /**
   * 获取与指定用户的聊天记录（共用接口）
   * @param userId 对方用户ID
   * @param isSeller 是否为商家调用（true:商家会标记已读，false:用户不标记）
   */
  getConversation: (userId: number, isSeller: boolean = false) => {
      return api.get(`/chat/conversation/${userId}`, { params: { isSeller } })
  },

  /**
   * 获取所有会话列表（商家端）
   */
  getConversations: () => {
      return api.get('/chat/conversations')
  },

  /**
   * 获取未读消息总数（商家端）
   */
  getUnreadCount: () => {
      return api.get('/chat/unread/count')
  },

  /**
   * 标记所有消息为已读（商家端）
   */
  markAllMessagesRead: () => {
      return api.put('/chat/read/all')
  }

}