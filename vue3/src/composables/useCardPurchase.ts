import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { userAPI } from '@/api/userAPI'
import { type CardType } from '@/api/adminAPI'
import { message } from '@/utils/message'

/**
 * 会员卡购买流程（首页推荐卡 / 全部卡页共用，保持购买逻辑一致）
 * 流程：创建订单 → 发起支付（支付宝沙箱表单 / Mock 直接成功）
 */
export function useCardPurchase() {
  const router = useRouter()
  const purchasingId = ref<number | null>(null)

  const handleBuy = async (c: CardType) => {
    if (purchasingId.value !== null) return
    await payOrderFlow(c, null)
  }

  /**
   * 创建订单（可选 userCouponId 使用优惠券）→ 发起支付。
   * 金额（活动价 / 券抵扣）统一由服务端按下单时点重算，前端展示仅供参考。
   */
  const payOrderFlow = async (c: CardType, userCouponId?: number | null) => {
    if (purchasingId.value !== null) return
    purchasingId.value = c.id
    try {
      // 1. 创建订单（携带优惠券）
      const createRes = await userAPI.createOrder(c.id, userCouponId ?? null)
      if (!createRes.success || !createRes.data?.orderNo) return

      // 2. 发起支付
      const payRes = await userAPI.payOrder(createRes.data.orderNo)
      if (!payRes.success) return

      const data = payRes.data
      if (data?.payUrl) {
        // === Alipay 模式（优先）：顶层导航直达支付宝收银台 ===
        // location.href 是浏览器最基础的跳转，永不拦截，本机/服务器行为一致，
        // 彻底规避 window.open + document.write / 隐藏容器 form.submit() 被拦截导致空白页。
        window.location.href = data.payUrl
      } else if (data?.payFormHtml) {
        // === Alipay 模式（兜底）：后端未返回 payUrl 时，注入隐藏容器提交表单 ===
        submitAlipayForm(data.payFormHtml)
      } else {
        // === Mock 模式：直接支付成功 ===
        message.success('支付成功，会员卡已生成')
        router.replace('/user/membership')
      }
    } catch {
      /* 错误由拦截器统一提示 */
    } finally {
      purchasingId.value = null
    }
  }

  /**
   * 兜底路径：在当前页注入隐藏容器并 submit 表单。
   * 仅当后端未能返回 payUrl 时使用；正常情况下走 location.href。
   */
  const submitAlipayForm = (html: string) => {
    const container = document.createElement('div')
    container.style.display = 'none'
    container.innerHTML = html
    document.body.appendChild(container)
    const form = container.querySelector('form')
    if (form) {
      form.target = '_self'
      form.submit()
    }
  }

  return { purchasingId, handleBuy, payOrderFlow }
}
