/**
 * 全局确认弹窗（单例）
 *
 * 用法：
 *   import { confirm } from '@/composables/useConfirm'
 *   const ok = await confirm({ content: '确认删除「XXX」吗？删除后不可恢复。' })
 *   if (!ok) return
 *   // 执行删除...
 *
 * 需要收集一段可选文本（替代 window.prompt）：
 *   import { confirmInput } from '@/composables/useConfirm'
 *   const remark = await confirmInput({
 *     title: '处理事件', content: '确认处理？', inputLabel: '处理备注', inputPlaceholder: '选填'
 *   })
 *   if (remark === null) return  // 用户取消
 *   // remark 为输入值（可能为空字符串）
 *
 * 在 Layout.vue 中通过 <ConfirmDialog /> 挂载唯一实例，
 * 各页面只需调用 confirm() / confirmInput() 即可弹出统一风格的确认弹窗。
 */
import { ref } from 'vue'

export interface ConfirmOptions {
  /** 弹窗标题，默认"删除确认" */
  title?: string
  /** 弹窗正文 */
  content: string
  /** 确认按钮文字，默认"确认删除" */
  confirmText?: string
  /** 取消按钮文字，默认"取消" */
  cancelText?: string
  /** 确认按钮是否为危险色（红色），默认 true */
  danger?: boolean
}

export interface ConfirmInputOptions extends ConfirmOptions {
  /** 若设置，弹窗内显示一个文本输入框（替代 window.prompt） */
  inputLabel?: string
  /** 输入框占位提示 */
  inputPlaceholder?: string
}

const visible = ref(false)
const title = ref('删除确认')
const content = ref('')
const confirmText = ref('确认删除')
const cancelText = ref('取消')
const danger = ref(true)
const inputLabel = ref('')
const inputPlaceholder = ref('')
const inputValue = ref('')

interface ConfirmResult { ok: boolean; value: string }
let resolver: ((value: ConfirmResult) => void) | null = null

/** 弹出确认弹窗，返回 Promise<boolean>。点击"确认" → true；取消/关闭 → false。 */
function confirm(options: ConfirmOptions): Promise<boolean> {
  title.value = options.title ?? '删除确认'
  content.value = options.content
  confirmText.value = options.confirmText ?? '确认删除'
  cancelText.value = options.cancelText ?? '取消'
  danger.value = options.danger ?? true
  inputLabel.value = ''
  inputValue.value = ''
  visible.value = true
  return new Promise<boolean>(resolve => {
    resolver = r => resolve(r.ok)
  })
}

/**
 * 弹出带可选输入框的确认弹窗，返回 Promise<string | null>。
 * - 用户点击"确认" → 返回输入值（可能为空字符串）
 * - 用户取消/关闭 → 返回 null
 */
function confirmInput(options: ConfirmInputOptions): Promise<string | null> {
  title.value = options.title ?? '请确认'
  content.value = options.content
  confirmText.value = options.confirmText ?? '确认'
  cancelText.value = options.cancelText ?? '取消'
  danger.value = options.danger ?? false
  inputLabel.value = options.inputLabel ?? ''
  inputPlaceholder.value = options.inputPlaceholder ?? ''
  inputValue.value = ''
  visible.value = true
  return new Promise<string | null>(resolve => {
    resolver = r => resolve(r.ok ? r.value : null)
  })
}

function handleConfirm() {
  visible.value = false
  resolver?.({ ok: true, value: inputValue.value })
  resolver = null
}

function handleCancel() {
  visible.value = false
  resolver?.({ ok: false, value: '' })
  resolver = null
}

/** 供 ConfirmDialog 组件使用的响应式状态与回调 */
export function useConfirmDialog() {
  return {
    visible,
    title,
    content,
    confirmText,
    cancelText,
    danger,
    inputLabel,
    inputPlaceholder,
    inputValue,
    handleConfirm,
    handleCancel,
  }
}

export { confirm, confirmInput }
