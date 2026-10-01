<template>
  <div class="memberships">
    <!-- 操作栏：折叠/展开筛选 + 导出 -->
    <div class="action-bar">
      <button class="filter-toggle" @click="filtersCollapsed = !filtersCollapsed">
        <i :class="filtersCollapsed ? 'fas fa-sliders-h' : 'fas fa-sliders-h'"></i>
        筛选条件
        <span class="filter-count" v-if="activeFilterCount">{{ activeFilterCount }}</span>
        <i class="fas fa-chevron-down chevron" :class="{ open: !filtersCollapsed }"></i>
      </button>
      <button class="btn-export" :disabled="exporting" @click="onExport">
        <i class="fas fa-download"></i> {{ exporting ? '导出中...' : '导出 Excel' }}
      </button>
    </div>

    <!-- 筛选区（可折叠） -->
    <div class="filter-panel" v-show="!filtersCollapsed">
      <div class="toolbar">
        <select v-model="filter.status" class="filter-select">
          <option value="">全部状态</option>
          <option value="UNACTIVATED">未激活</option>
          <option value="ACTIVE">生效中</option>
          <option value="EXPIRED">已过期</option>
          <option value="DISABLED">已停用</option>
        </select>
        <select v-model="filter.storeId" class="filter-select">
          <option :value="null">全部门店</option>
          <option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <select v-model="filter.cardTypeId" class="filter-select">
          <option :value="null">全部卡种</option>
          <option v-for="c in cardTypes" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <input
          v-model.trim="filter.phone"
          class="search-input"
          placeholder="搜索手机号 / 卡号尾号"
          @keyup.enter="onSearch"
        />
        <button class="btn-search" @click="onSearch"><i class="fas fa-search"></i> 查询</button>
        <button class="btn-reset" @click="onReset" v-if="activeFilterCount">重置</button>
      </div>
    </div>

    <div class="table-wrap card">
      <table class="data-table">
        <thead>
          <tr>
            <th>卡号</th>
            <th>手机号</th>
            <th>卡类型</th>
            <th>次数</th>
            <th>状态</th>
            <th>激活时间</th>
            <th>到期时间</th>
            <th class="ops-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in list" :key="m.id">
            <td class="cardno-cell">{{ m.cardNo || '-' }}</td>
            <td>{{ m.phone || '-' }}</td>
            <td>
              <div class="cell-type">
                <span class="type-name">{{ m.cardTypeName || m.cardType || '-' }}</span>
                <span class="tag" :class="m.category === 'PT' ? 'tag-pt' : 'tag-normal'">
                  {{ categoryText(m.category) }}
                </span>
              </div>
            </td>
            <td>
              <span v-if="m.totalTimes !== null && m.totalTimes !== undefined" class="times-val">
                {{ m.remainingTimes ?? 0 }}<span class="times-total">/{{ m.totalTimes }}</span>
              </span>
              <span v-else class="muted">—</span>
            </td>
            <td>
              <span class="tag" :class="cardStatusClass(m.status)">{{ cardStatusText(m.status) }}</span>
            </td>
            <td class="nowrap">
              <span v-if="m.startTime">{{ formatDate(m.startTime) }}</span>
              <span v-else class="muted">未激活</span>
            </td>
            <td class="nowrap">{{ m.endTime ? formatDate(m.endTime) : '-' }}</td>
            <td class="ops-cell">
              <!-- 主操作（前 2 个） -->
              <button
                v-for="(a, i) in primaryActions(m)"
                :key="i"
                class="op-btn"
                :class="a.danger ? 'danger' : ''"
                :disabled="loadingId === m.id"
                @click="a.handler()"
              >{{ a.label }}</button>
              <!-- 更多下拉 -->
              <div class="more-wrap" v-if="secondaryActions(m).length">
                <button class="op-btn more-btn" @click="toggleMore(m.id)">更多 <i class="fas fa-chevron-down"></i></button>
                <div class="more-menu" v-if="moreId === m.id">
                  <button
                    v-for="(a, i) in secondaryActions(m)"
                    :key="i"
                    class="more-item"
                    :class="a.danger ? 'danger' : ''"
                    :disabled="loadingId === m.id"
                    @click="a.handler()"
                  >
                    <i :class="a.icon || 'fas fa-cog'"></i>{{ a.label }}
                  </button>
                </div>
              </div>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="8" class="empty-row">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 点击空白关闭"更多"菜单 -->
    <div class="more-backdrop" v-if="moreId !== null" @click="moreId = null"></div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <button class="page-btn" :disabled="page <= 1" @click="prevPage">上一页</button>
      <span class="page-info">第 {{ page }} 页 / 共 {{ totalPages }} 页（共 {{ total }} 条）</span>
      <button class="page-btn" :disabled="page >= totalPages" @click="nextPage">下一页</button>
    </div>

    <!-- ============ 详情弹窗（字段分组） ============ -->
    <div class="modal-mask" v-if="detailVisible" @click.self="closeDetail">
      <div class="modal modal-lg">
        <div class="modal-header">
          <span class="modal-title">会员卡详情 · {{ detail?.detail.cardNo || '-' }}</span>
          <button class="modal-close" @click="closeDetail"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body" v-if="detail">
          <!-- 基本信息 -->
          <div class="section">
            <div class="section-title">基本信息</div>
            <div class="info-grid">
              <div class="info-item"><span class="info-label">卡号</span><span class="info-value">{{ detail.detail.cardNo || '-' }}</span></div>
              <div class="info-item"><span class="info-label">手机号</span><span class="info-value">{{ detail.detail.phone || '-' }}</span></div>
              <div class="info-item"><span class="info-label">卡类型</span><span class="info-value">{{ detail.detail.cardTypeName || detail.detail.cardType || '-' }}</span></div>
              <div class="info-item"><span class="info-label">分类</span><span class="info-value">{{ categoryText(detail.detail.category) }}</span></div>
              <div class="info-item"><span class="info-label">适用范围</span><span class="info-value">{{ scopeText(detail.detail) }}</span></div>
              <div class="info-item"><span class="info-label">门店</span><span class="info-value">{{ detail.detail.storeName || '-' }}</span></div>
            </div>
          </div>

          <!-- 卡状态 -->
          <div class="section">
            <div class="section-title">卡状态</div>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">状态</span>
                <span class="info-value">
                  <span class="tag" :class="cardStatusClass(detail.detail.status)">{{ cardStatusText(detail.detail.status) }}</span>
                </span>
              </div>
              <div class="info-item"><span class="info-label">总次数</span><span class="info-value">{{ detail.detail.totalTimes ?? '—' }}</span></div>
              <div class="info-item"><span class="info-label">剩余次数</span><span class="info-value">{{ detail.detail.remainingTimes ?? '—' }}</span></div>
            </div>
          </div>

          <!-- 时间信息 -->
          <div class="section">
            <div class="section-title">时间信息</div>
            <div class="info-grid">
              <div class="info-item"><span class="info-label">办理时间</span><span class="info-value">{{ formatDate(detail.detail.createdAt) }}</span></div>
              <div class="info-item">
                <span class="info-label">激活/生效时间</span>
                <span class="info-value">{{ detail.detail.startTime ? formatDate(detail.detail.startTime) : '未激活' }}</span>
              </div>
              <div class="info-item"><span class="info-label">到期时间</span><span class="info-value">{{ detail.detail.endTime ? formatDate(detail.detail.endTime) : '-' }}</span></div>
              <div class="info-item"><span class="info-label">更新时间</span><span class="info-value">{{ detail.detail.updatedAt ? formatDate(detail.detail.updatedAt) : '-' }}</span></div>
            </div>
          </div>

          <!-- 操作记录 -->
          <div class="section">
            <div class="section-title">操作记录</div>
            <div class="table-wrap inner">
              <table class="data-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>操作人</th>
                    <th>动作</th>
                    <th>参数</th>
                    <th>结果</th>
                    <th>IP</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="log in detail.logs" :key="log.id">
                    <td class="nowrap">{{ formatDate(log.createdAt) }}</td>
                    <td>{{ log.username || '-' }}</td>
                    <td class="nowrap">{{ log.action }}{{ actionName(log.uri) ? ' · ' + actionName(log.uri) : '' }}</td>
                    <td class="param-cell">{{ log.paramSummary || '-' }}</td>
                    <td>
                      <span class="tag" :class="log.result === 'SUCCESS' ? 'tag-active' : 'tag-disabled'">
                        {{ log.result }}
                      </span>
                    </td>
                    <td class="nowrap">{{ log.ip || '-' }}</td>
                  </tr>
                  <tr v-if="!detail.logs.length">
                    <td colspan="6" class="empty-row">暂无操作记录</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="modal-body loading-row" v-else>加载中...</div>
        <div class="modal-footer">
          <button class="btn-cancel" :disabled="detailLoading" @click="closeDetail">关闭</button>
        </div>
      </div>
    </div>

    <!-- ============ 停用弹窗 ============ -->
    <div class="modal-mask" v-if="disableVisible" @click.self="closeDisable">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">停用会员卡 · {{ disableTarget?.cardNo }}</span>
          <button class="modal-close" @click="closeDisable"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-hint-row" v-if="disableTarget">
            手机号 {{ disableTarget.phone || '-' }}，当前状态 {{ cardStatusText(disableTarget.status) }}
          </div>
          <div class="form-row">
            <label class="form-label">停用原因</label>
            <textarea v-model.trim="disableReason" class="form-input textarea" maxlength="200" placeholder="选填，将记入操作日志"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" :disabled="loadingId !== null" @click="closeDisable">取消</button>
          <button class="btn-confirm danger" :disabled="loadingId !== null" @click="doDisable">确认停用</button>
        </div>
      </div>
    </div>

    <!-- ============ 延期弹窗 ============ -->
    <div class="modal-mask" v-if="extendVisible" @click.self="closeExtend">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">延期会员卡 · {{ extendTarget?.cardNo }}</span>
          <button class="modal-close" @click="closeExtend"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-hint-row" v-if="extendTarget">
            当前到期时间 {{ extendTarget.endTime ? formatDate(extendTarget.endTime) : '-' }}
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>延期天数</label>
            <input v-model.trim="extendDays" class="form-input" type="number" min="1" placeholder="如：30" />
          </div>
          <div class="form-row">
            <label class="form-label">原因</label>
            <textarea v-model.trim="extendReason" class="form-input textarea" maxlength="200" placeholder="选填"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" :disabled="loadingId !== null" @click="closeExtend">取消</button>
          <button class="btn-confirm" :disabled="loadingId !== null" @click="doExtend">确认延期</button>
        </div>
      </div>
    </div>

    <!-- ============ 调次数弹窗 ============ -->
    <div class="modal-mask" v-if="adjustVisible" @click.self="closeAdjust">
      <div class="modal">
        <div class="modal-header">
          <span class="modal-title">调整剩余次数 · {{ adjustTarget?.cardNo }}</span>
          <button class="modal-close" @click="closeAdjust"><i class="fas fa-times"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-hint-row" v-if="adjustTarget">
            当前剩余 {{ adjustTarget.remainingTimes ?? 0 }} / 总次数 {{ adjustTarget.totalTimes ?? 0 }}
          </div>
          <div class="form-row">
            <label class="form-label"><span class="req">*</span>新剩余次数</label>
            <input v-model.trim="adjustValue" class="form-input" type="number" min="0" placeholder="如：10" />
          </div>
          <div class="form-row">
            <label class="form-label">原因</label>
            <textarea v-model.trim="adjustReason" class="form-input textarea" maxlength="200" placeholder="选填"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-cancel" :disabled="loadingId !== null" @click="closeAdjust">取消</button>
          <button class="btn-confirm" :disabled="loadingId !== null" @click="doAdjust">确认调整</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  getMemberships,
  getMembershipDetail,
  disableMembership,
  activateMembership,
  extendMembership,
  adjustMembershipTimes,
  getCardTypes,
  exportMembershipsUrl,
  type MembershipRecord,
  type MembershipFilter,
  type MembershipDetail,
  type CardType
} from '@/api/adminAPI'
import { getStores, type Store } from '@/api/storeAPI'
import { downloadExport } from '@/api/statsAPI'
import { message } from '@/utils/message'

defineOptions({ name: 'AdminMemberships' })

interface OpAction {
  label: string
  handler: () => void
  danger?: boolean
  icon?: string
}

// ============ 下拉数据 ============
const stores = ref<Store[]>([])
const cardTypes = ref<CardType[]>([])

// ============ 筛选条件 ============
const filter = reactive<{
  status: string
  storeId: number | null
  cardTypeId: number | null
  phone: string
}>({
  status: '',
  storeId: null,
  cardTypeId: null,
  phone: ''
})

const filtersCollapsed = ref(false)
const activeFilterCount = computed(() => {
  let n = 0
  if (filter.status) n++
  if (filter.storeId) n++
  if (filter.cardTypeId) n++
  if (filter.phone) n++
  return n
})

// ============ 列表 + 分页 ============
const list = ref<MembershipRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const totalPages = computed(() => Math.ceil(total.value / size.value) || 1)

const loadingId = ref<number | null>(null)
const exporting = ref(false)

const loadStores = async () => {
  try {
    const res = await getStores()
    if (res.success) stores.value = res.data?.list || []
  } catch (e) {
    /* 拦截器统一提示 */
  }
}

const loadCardTypes = async () => {
  try {
    const res = await getCardTypes()
    if (res.success) cardTypes.value = res.data?.list || []
  } catch (e) {
    /* 拦截器统一提示 */
  }
}

const buildParams = (): MembershipFilter => {
  const p: MembershipFilter = { page: page.value, size: size.value }
  if (filter.status) p.status = filter.status
  if (filter.storeId) p.storeId = filter.storeId
  if (filter.cardTypeId) p.cardTypeId = filter.cardTypeId
  if (filter.phone) {
    // 单搜索框同时匹配手机号与卡号尾号：
    // - 始终按手机号模糊匹配（保留原行为）
    // - 输入 4 位及以上时同时按卡号尾号匹配（避免短输入命中过多卡号）
    p.phone = filter.phone
    if (filter.phone.length >= 4) p.cardNo = filter.phone
  }
  return p
}

const loadList = async () => {
  try {
    const res = await getMemberships(buildParams())
    if (res.success) {
      list.value = res.data?.list || []
      total.value = res.data?.total || 0
    }
  } catch (e) {
    /* 拦截器统一提示 */
  }
}

const onSearch = () => {
  page.value = 1
  loadList()
}

const onReset = () => {
  filter.status = ''
  filter.storeId = null
  filter.cardTypeId = null
  filter.phone = ''
  page.value = 1
  loadList()
}

const onExport = async () => {
  if (exporting.value) return
  exporting.value = true
  try {
    const todayTag = new Date().toISOString().slice(0, 10)
    await downloadExport(exportMembershipsUrl(buildParams()), undefined, `会员卡明细_${todayTag}.xlsx`)
    message.success('导出已开始')
  } catch (e: any) {
    message.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const prevPage = () => {
  if (page.value > 1) {
    page.value--
    loadList()
  }
}

const nextPage = () => {
  if (page.value < totalPages.value) {
    page.value++
    loadList()
  }
}

// ============ 显示工具 ============
const cardStatusText = (s: string): string => {
  switch (s) {
    case 'UNACTIVATED': return '未激活'
    case 'ACTIVE': return '生效中'
    case 'EXPIRED': return '已过期'
    case 'DISABLED': return '已停用'
    default: return s
  }
}

const cardStatusClass = (s: string): string => {
  switch (s) {
    case 'UNACTIVATED': return 'tag-unactivated'
    case 'ACTIVE': return 'tag-active'
    case 'EXPIRED': return 'tag-expired'
    case 'DISABLED': return 'tag-disabled'
    default: return 'tag-none'
  }
}

const categoryText = (category: string | null | undefined): string =>
  category === 'PT' ? '私教课卡' : '普通卡'

const scopeText = (m: MembershipRecord): string => {
  if (m.scope === 'ALL_STORE') return '全店通用'
  return m.storeName || '指定门店'
}

const formatDate = (t: string | null | undefined): string => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 16)
}

const actionName = (uri: string | null): string => {
  if (!uri) return ''
  const seg = uri.split('/').filter(Boolean).pop() || ''
  switch (seg) {
    case 'activate': return '激活'
    case 'disable': return '停用'
    case 'extend': return '延期'
    case 'times': return '调次数'
    default: return seg
  }
}

// ============ 操作按钮：主操作 + 次操作（>3 折叠到"更多"） ============
const allActions = (m: MembershipRecord): OpAction[] => {
  const arr: OpAction[] = [{ label: '详情', icon: 'fas fa-eye', handler: () => openDetail(m) }]
  if (m.status === 'UNACTIVATED') {
    arr.push({ label: '激活', icon: 'fas fa-bolt', handler: () => doActivate(m) })
  }
  if (m.status !== 'DISABLED') {
    arr.push({ label: '停用', danger: true, icon: 'fas fa-ban', handler: () => openDisable(m) })
  }
  if (m.status === 'ACTIVE' || m.status === 'EXPIRED') {
    arr.push({ label: '延期', icon: 'fas fa-clock', handler: () => openExtend(m) })
  }
  if (m.totalTimes !== null && m.totalTimes !== undefined) {
    arr.push({ label: '调次数', icon: 'fas fa-sliders-h', handler: () => openAdjust(m) })
  }
  return arr
}

// 前 2 个内联显示
const primaryActions = (m: MembershipRecord): OpAction[] => allActions(m).slice(0, 2)
// 剩余折叠到"更多"菜单
const secondaryActions = (m: MembershipRecord): OpAction[] => allActions(m).slice(2)

const moreId = ref<number | null>(null)
const toggleMore = (id: number) => {
  moreId.value = moreId.value === id ? null : id
}

// ============ 详情弹窗 ============
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<MembershipDetail | null>(null)

const openDetail = async (m: MembershipRecord) => {
  moreId.value = null
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    const res = await getMembershipDetail(m.id)
    if (res.success) detail.value = res.data
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  if (detailLoading.value) return
  detailVisible.value = false
  detail.value = null
}

// ============ 激活 ============
const doActivate = async (m: MembershipRecord) => {
  moreId.value = null
  if (loadingId.value !== null) return
  loadingId.value = m.id
  try {
    const res = await activateMembership(m.id)
    if (res.success) {
      message.success('激活成功')
      await loadList()
    }
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    loadingId.value = null
  }
}

// ============ 停用 ============
const disableVisible = ref(false)
const disableTarget = ref<MembershipRecord | null>(null)
const disableReason = ref('')

const openDisable = (m: MembershipRecord) => {
  moreId.value = null
  disableTarget.value = m
  disableReason.value = ''
  disableVisible.value = true
}

const closeDisable = () => {
  if (loadingId.value !== null) return
  disableVisible.value = false
  disableTarget.value = null
  disableReason.value = ''
}

const doDisable = async () => {
  const m = disableTarget.value
  if (!m) return
  if (loadingId.value !== null) return
  loadingId.value = m.id
  try {
    const res = await disableMembership(m.id, disableReason.value || undefined)
    if (res.success) {
      message.success('已停用')
      disableVisible.value = false
      disableTarget.value = null
      disableReason.value = ''
      await loadList()
    }
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    loadingId.value = null
  }
}

// ============ 延期 ============
const extendVisible = ref(false)
const extendTarget = ref<MembershipRecord | null>(null)
const extendDays = ref('')
const extendReason = ref('')

const openExtend = (m: MembershipRecord) => {
  moreId.value = null
  extendTarget.value = m
  extendDays.value = ''
  extendReason.value = ''
  extendVisible.value = true
}

const closeExtend = () => {
  if (loadingId.value !== null) return
  extendVisible.value = false
  extendTarget.value = null
  extendDays.value = ''
  extendReason.value = ''
}

const doExtend = async () => {
  const m = extendTarget.value
  if (!m) return
  if (loadingId.value !== null) return
  const days = Number(extendDays.value)
  if (!extendDays.value || !Number.isFinite(days) || days <= 0) {
    message.warning('请输入有效的延期天数')
    return
  }
  loadingId.value = m.id
  try {
    const res = await extendMembership(m.id, Math.floor(days), extendReason.value || undefined)
    if (res.success) {
      message.success('延期成功')
      extendVisible.value = false
      extendTarget.value = null
      extendDays.value = ''
      extendReason.value = ''
      await loadList()
    }
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    loadingId.value = null
  }
}

// ============ 调次数 ============
const adjustVisible = ref(false)
const adjustTarget = ref<MembershipRecord | null>(null)
const adjustValue = ref('')
const adjustReason = ref('')

const openAdjust = (m: MembershipRecord) => {
  moreId.value = null
  adjustTarget.value = m
  adjustValue.value = ''
  adjustReason.value = ''
  adjustVisible.value = true
}

const closeAdjust = () => {
  if (loadingId.value !== null) return
  adjustVisible.value = false
  adjustTarget.value = null
  adjustValue.value = ''
  adjustReason.value = ''
}

const doAdjust = async () => {
  const m = adjustTarget.value
  if (!m) return
  if (loadingId.value !== null) return
  const val = Number(adjustValue.value)
  if (adjustValue.value === '' || !Number.isFinite(val) || val < 0) {
    message.warning('请输入有效的新剩余次数')
    return
  }
  loadingId.value = m.id
  try {
    const res = await adjustMembershipTimes(m.id, Math.floor(val), adjustReason.value || undefined)
    if (res.success) {
      message.success('次数已调整')
      adjustVisible.value = false
      adjustTarget.value = null
      adjustValue.value = ''
      adjustReason.value = ''
      await loadList()
    }
  } catch (e) {
    /* 拦截器统一提示 */
  } finally {
    loadingId.value = null
  }
}

const route = useRoute()
onMounted(async () => {
  // 支持从会员管理页按手机号跳转预填筛选
  if (route.query.phone) {
    filter.phone = String(route.query.phone)
    filtersCollapsed.value = false
  }
  await Promise.all([loadStores(), loadCardTypes()])
  await loadList()
})
</script>

<style scoped>
.memberships {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ============ 操作栏（折叠/展开 + 导出） ============ */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.filter-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background-color: var(--color-bg-gray);
  color: var(--color-text);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
}

.filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background-color: var(--color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.chevron {
  margin-left: 2px;
  font-size: 11px;
  transition: transform 0.2s;
  color: var(--color-text-secondary);
}

.chevron.open {
  transform: rotate(180deg);
}

.filter-panel {
  background-color: var(--color-bg);
  border-radius: var(--radius-sm);
  padding: 12px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  align-items: center;
}

.filter-select {
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  background-color: #fff;
  min-width: 120px;
}

.filter-select:focus {
  border-color: var(--color-primary);
}

.search-input {
  flex: 1;
  max-width: 200px;
  padding: 7px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
}

.search-input:focus {
  border-color: var(--color-primary);
}

.btn-search {
  padding: 8px 16px;
  background-color: var(--color-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.btn-reset {
  padding: 8px 16px;
  background-color: #fff;
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.btn-export {
  padding: 8px 16px;
  background-color: var(--color-success);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.btn-export:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ============ 表格 ============ */
.table-wrap {
  overflow-x: auto;
}

.table-wrap.inner {
  max-height: 280px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th,
.data-table td {
  padding: 12px;
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.data-table th {
  color: var(--color-text-secondary);
  font-weight: 600;
  background-color: var(--color-bg);
}

.empty-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 20px 0;
}

.loading-row {
  text-align: center;
  color: var(--color-text-placeholder);
  padding: 40px 0;
}

.nowrap {
  white-space: nowrap;
}

.cardno-cell {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 13px;
  white-space: nowrap;
}

.muted {
  color: var(--color-text-placeholder);
}

.cell-type {
  display: flex;
  flex-direction: column;
  gap: 3px;
  white-space: nowrap;
}

.type-name {
  font-weight: 600;
}

.times-val {
  font-weight: 700;
  white-space: nowrap;
}

.times-total {
  color: var(--color-text-secondary);
  font-weight: 400;
}

/* ============ 操作列 + 更多菜单 ============ */
.ops-col {
  min-width: 150px;
}

.ops-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  position: relative;
}

.op-btn {
  padding: 3px 9px;
  background-color: rgba(24, 144, 255, 0.08);
  color: var(--color-primary);
  font-size: 12px;
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.op-btn.danger {
  background-color: rgba(255, 77, 79, 0.08);
  color: var(--color-danger);
}

.op-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.more-wrap {
  position: relative;
}

.more-btn i {
  font-size: 10px;
  margin-left: 2px;
}

.more-menu {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  background-color: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 50;
  min-width: 120px;
  padding: 4px 0;
}

.more-item {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px 12px;
  background-color: transparent;
  color: var(--color-text);
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
}

.more-item:hover {
  background-color: var(--color-bg);
}

.more-item.danger {
  color: var(--color-danger);
}

.more-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.more-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
}

/* ============ 标签 ============ */
.tag {
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
  white-space: nowrap;
}

.tag-none {
  background-color: rgba(144, 147, 153, 0.1);
  color: var(--color-text-placeholder);
}

.tag-normal {
  background-color: rgba(24, 144, 255, 0.12);
  color: #1890ff;
}

.tag-pt {
  background-color: rgba(255, 158, 0, 0.12);
  color: #ff9e00;
}

.tag-unactivated {
  background-color: rgba(255, 158, 0, 0.12);
  color: #ff9e00;
}

.tag-active {
  background-color: rgba(82, 196, 26, 0.15);
  color: var(--color-success);
}

.tag-expired,
.tag-disabled {
  background-color: rgba(245, 108, 108, 0.12);
  color: var(--color-danger);
}

/* ============ 分页 ============ */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: var(--spacing-sm);
}

.page-btn {
  padding: 8px 16px;
  background-color: var(--color-bg);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* ============ 弹窗 ============ */
.modal-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal {
  width: 460px;
  max-width: 92vw;
  max-height: 88vh;
  background-color: #fff;
  border-radius: var(--radius-md);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: modalIn 0.2s ease;
}

.modal.modal-lg {
  width: 760px;
}

@keyframes modalIn {
  from { transform: translateY(-12px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--color-text-secondary);
  font-size: 15px;
}

.modal-close:hover {
  background-color: var(--color-bg-gray);
  color: var(--color-text);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  padding: 14px 20px;
  border-top: 1px solid var(--color-border);
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.req {
  color: var(--color-danger);
  margin-right: 2px;
}

.form-input {
  padding: 8px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  font-size: 14px;
  outline: none;
  font-family: inherit;
}

.form-input:focus {
  border-color: var(--color-primary);
}

.textarea {
  resize: vertical;
  min-height: 76px;
  line-height: 1.5;
}

.form-hint-row {
  font-size: 13px;
  color: var(--color-text-secondary);
  background-color: var(--color-bg);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
}

.btn-cancel {
  padding: 8px 20px;
  background-color: var(--color-bg-gray);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  font-size: 14px;
}

.btn-cancel:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-confirm {
  padding: 8px 20px;
  background: var(--gradient-primary);
  color: #fff;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 600;
}

.btn-confirm.danger {
  background: var(--color-danger);
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ============ 详情分组 ============ */
.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  padding-left: 8px;
  border-left: 3px solid var(--color-primary);
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 16px;
  padding: 12px 14px;
  background-color: var(--color-bg);
  border-radius: var(--radius-sm);
}

.info-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  align-items: baseline;
}

.info-label {
  color: var(--color-text-secondary);
  min-width: 80px;
  flex-shrink: 0;
}

.info-value {
  color: var(--color-text);
  word-break: break-all;
}
</style>
